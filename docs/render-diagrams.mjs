/*
 * Copyright 2026 the Small CRM authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Renders every Mermaid block in architecture.md to a PNG.
 *
 *   node docs/render-diagrams.mjs
 *
 * architecture.md stays the source: the blocks in it are what GitHub renders, and what this
 * reads. The PNGs are for looking at the diagrams without a Mermaid-aware viewer — open
 * docs/diagrams/index.html in a browser.
 *
 * Mermaid needs a headless Chromium, which is not a dependency this project otherwise has, so
 * it is borrowed from the mermaid-cli container image rather than installed:
 *
 *   podman pull ghcr.io/mermaid-js/mermaid-cli/mermaid-cli
 *
 * Each diagram is rendered twice, once per theme, because the page they end up on follows the
 * reader's.
 */

import { execFileSync } from "node:child_process";
import {
  mkdirSync,
  readFileSync,
  readdirSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const DOCS = dirname(fileURLToPath(import.meta.url));
const PROJECT = resolve(DOCS, "..");
const SOURCE = join(DOCS, "architecture.md");
const OUTPUT = join(DOCS, "diagrams");
/** Scratch space inside the project, because the podman VM only mounts the home directory. */
const WORK = join(PROJECT, "target", "mermaid");
const IMAGE = "ghcr.io/mermaid-js/mermaid-cli/mermaid-cli";

const THEMES = [
  { name: "light", mermaid: "default", background: "white" },
  { name: "dark", mermaid: "dark", background: "transparent" },
];

/** Turns a heading into a file name stem: "1. Persistence model" -> "01-persistence-model". */
function slug(index, heading) {
  const text = heading
    .toLowerCase()
    .replace(/^\d+\.\s*/, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");
  return `${String(index).padStart(2, "0")}-${text}`;
}

/** Every fenced Mermaid block, tagged with the heading it sits under. */
function extractDiagrams(markdown) {
  const diagrams = [];
  let heading = "diagram";
  let fence = null;
  for (const line of markdown.split("\n")) {
    if (fence === null && line.startsWith("#")) {
      heading = line.replace(/^#+\s*/, "").trim();
    } else if (fence === null && line.trim() === "```mermaid") {
      fence = [];
    } else if (fence !== null && line.trim() === "```") {
      diagrams.push({ heading, source: fence.join("\n") });
      fence = null;
    } else if (fence !== null) {
      fence.push(line);
    }
  }
  if (fence !== null) {
    throw new Error("architecture.md has an unterminated ```mermaid block");
  }
  return diagrams;
}

/** Two diagrams under one heading need telling apart. */
function nameDiagrams(diagrams) {
  const used = new Map();
  return diagrams.map((diagram, index) => {
    const stem = slug(index + 1, diagram.heading);
    const seen = (used.get(diagram.heading) ?? 0) + 1;
    used.set(diagram.heading, seen);
    return { ...diagram, stem, ordinal: seen };
  });
}

function render(named) {
  rmSync(WORK, { recursive: true, force: true });
  mkdirSync(WORK, { recursive: true });
  mkdirSync(OUTPUT, { recursive: true });
  for (const file of readdirSync(OUTPUT)) {
    if (file.endsWith(".png")) {
      rmSync(join(OUTPUT, file));
    }
  }

  for (const theme of THEMES) {
    console.log(`Rendering ${named.length} diagrams (${theme.name})`);
    // mermaid-cli reads the Markdown itself and renders every block in it, which keeps this
    // script out of the business of deciding what a Mermaid block is. One run per theme:
    // starting Chromium is the slow part, not the diagrams.
    execFileSync(
      "podman",
      [
        "run",
        "--rm",
        "-v",
        `${DOCS}:/docs:ro`,
        "-v",
        `${WORK}:/work`,
        "-v",
        `${OUTPUT}:/out`,
        IMAGE,
        "--input",
        "/docs/architecture.md",
        // Only written so mermaid-cli has somewhere to put its rewritten copy; the images beside
        // it are what this is for.
        "--output",
        `/work/${theme.name}.md`,
        "--artefacts",
        "/out",
        "--outputFormat",
        "png",
        "--theme",
        theme.mermaid,
        "--backgroundColor",
        theme.background,
        "--scale",
        "2",
        "--width",
        "1600",
        "--quiet",
      ],
      { stdio: "inherit" },
    );
    // Produced as "<output stem>-<n>.png", in the order the blocks appear.
    named.forEach((diagram, index) => {
      rename(
        join(OUTPUT, `${theme.name}-${index + 1}.png`),
        join(OUTPUT, `${diagram.stem}.${theme.name}.png`),
      );
    });
  }
}

function rename(from, to) {
  try {
    writeFileSync(to, readFileSync(from));
    rmSync(from);
  } catch (error) {
    throw new Error(`mermaid-cli produced no ${from}: ${error.message}`);
  }
}

/** A plain gallery, so the diagrams can be looked at without a Mermaid-aware viewer. */
function writeIndex(named) {
  const cards = named
    .map(
      (diagram) => `
      <figure id="${diagram.stem}">
        <figcaption>
          <span class="ordinal">${diagram.stem.slice(0, 2)}</span>
          ${escapeHtml(diagram.heading)}
        </figcaption>
        <picture>
          <source srcset="${diagram.stem}.dark.png" media="(prefers-color-scheme: dark)" />
          <img src="${diagram.stem}.light.png" alt="${escapeHtml(diagram.heading)}" />
        </picture>
      </figure>`,
    )
    .join("\n");

  writeFileSync(
    join(OUTPUT, "index.html"),
    `<!doctype html>
<!--
 Copyright 2026 the Small CRM authors.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.

 Generated by docs/render-diagrams.mjs — do not edit.
-->
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Small CRM — architecture diagrams</title>
    <style>
      :root {
        color-scheme: light dark;
        --ink: #16202c;
        --faint: #5d6b7a;
        --canvas: #f6f7f9;
        --surface: #ffffff;
        --line: #dfe3e8;
      }

      @media (prefers-color-scheme: dark) {
        :root {
          --ink: #e6ebf1;
          --faint: #9aa7b4;
          --canvas: #11161c;
          --surface: #171e26;
          --line: #2a333d;
        }
      }

      * {
        box-sizing: border-box;
      }

      body {
        margin: 0;
        padding: 3rem 1.5rem 5rem;
        font:
          16px/1.6 system-ui,
          -apple-system,
          "Segoe UI",
          sans-serif;
        color: var(--ink);
        background: var(--canvas);
      }

      main {
        max-width: 1180px;
        margin: 0 auto;
        display: flex;
        flex-direction: column;
        gap: 2.5rem;
      }

      header p {
        color: var(--faint);
        max-width: 62ch;
      }

      h1 {
        margin: 0 0 0.4rem;
        font-size: 1.9rem;
        letter-spacing: -0.02em;
      }

      figure {
        margin: 0;
        padding: 1.25rem;
        background: var(--surface);
        border: 1px solid var(--line);
        border-radius: 10px;
      }

      figcaption {
        display: flex;
        align-items: baseline;
        gap: 0.7rem;
        margin-bottom: 1rem;
        font-size: 1.05rem;
        font-weight: 600;
      }

      .ordinal {
        font-variant-numeric: tabular-nums;
        font-size: 0.8rem;
        letter-spacing: 0.08em;
        color: var(--faint);
      }

      picture {
        display: block;
        overflow-x: auto;
      }

      img {
        display: block;
        max-width: 100%;
        height: auto;
        margin: 0 auto;
      }

      nav {
        display: flex;
        flex-wrap: wrap;
        gap: 0.5rem 1.25rem;
        padding-bottom: 1.5rem;
        border-bottom: 1px solid var(--line);
      }

      nav a {
        color: var(--faint);
        text-decoration: none;
      }

      nav a:hover,
      nav a:focus-visible {
        color: var(--ink);
        text-decoration: underline;
      }
    </style>
  </head>
  <body>
    <main>
      <header>
        <h1>Small CRM — architecture diagrams</h1>
        <p>
          Rendered from the Mermaid blocks in
          <code>docs/architecture.md</code>, which is where the prose around them lives.
          Regenerate with <code>node docs/render-diagrams.mjs</code>.
        </p>
      </header>
      <nav>
${named.map((d) => `        <a href="#${d.stem}">${escapeHtml(d.heading)}</a>`).join("\n")}
      </nav>
${cards}
    </main>
  </body>
</html>
`,
  );
}

function escapeHtml(value) {
  return value.replace(
    /[&<>"]/g,
    (character) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[character],
  );
}

const named = nameDiagrams(extractDiagrams(readFileSync(SOURCE, "utf8")));
if (named.length === 0) {
  throw new Error("No Mermaid blocks found in architecture.md");
}
render(named);
writeIndex(named);
rmSync(WORK, { recursive: true, force: true });
console.log(`\n${named.length} diagrams -> ${OUTPUT}`);
console.log(`Open ${join(OUTPUT, "index.html")}`);
