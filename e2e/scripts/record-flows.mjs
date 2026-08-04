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

/**
 * Records a sequence diagram of every use-case the end-to-end suite drives.
 *
 * One use-case is one Playwright test. Each is run on its own, against its own freshly started
 * application whose recorder writes into that use-case's directory - which is what keeps the
 * diagrams of one use-case apart from the diagrams of the next without any labelling inside the
 * application. The packaged application has to be built with the recorder switched on first:
 *
 *   mvn package -DskipTests -Dcdi-flow.enabled=true
 *   node e2e/scripts/record-flows.mjs [--only <substring>] [--render] [--render-only]
 *
 *   --only <substring>  record just the use-cases whose title or spec contains the substring
 *   --render            render the combined diagram of each use-case to PNG (needs podman/docker)
 *   --render-chains     render the single application chains as well - dozens of images per
 *                       use-case, worth it locally and not worth committing
 *   --render-only       skip the recording and render what an earlier run left behind
 *
 * Rendering takes far longer than recording, so it is the last thing that happens: what was
 * recorded is written to `recorded.json` first, a use-case whose images are there already is
 * skipped, and `--render-only` picks an interrupted rendering back up without driving the
 * application again.
 *
 * Every request records a flow, and most of them repeat: reading a list, checking a session,
 * mapping an error. Identical chains - same participants, same calls, only other timings - are
 * therefore collapsed to the first file, with the number of occurrences noted in the index the
 * script writes beside them.
 *
 * The suite itself shares one database and runs in order, so a few of its tests expect data an
 * earlier one entered. A use-case that fails on its own is therefore recorded a second time with
 * its whole spec in front of it, and if it fails even then, whatever it did reach is kept and the
 * index says so. What the application did is recorded either way - a failed assertion is a
 * verdict on the test data, not on the call chain.
 */

import { execFileSync, spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import {
  copyFileSync,
  existsSync,
  mkdirSync,
  mkdtempSync,
  readdirSync,
  readFileSync,
  rmSync,
  unlinkSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { USE_CASE_DESCRIPTIONS } from './use-case-descriptions.mjs';

const E2E_DIR = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const PROJECT_DIR = resolve(E2E_DIR, '..');
const FLOW_DIR = join(PROJECT_DIR, 'docs', 'flows');

/** What was recorded, so a run of a single use-case can rewrite the index without losing the rest. */
const MANIFEST = 'recorded.json';

/** The stitched diagram of a whole use-case, written beside the chains it is made of. */
const COMBINED = 'use-case.mmd';

/** One document holding every use-case: what it does, and its diagram inline. */
const DOCUMENT = 'use-cases.md';

/**
 * A combined diagram of more blocks than this is written but not rendered: a use-case that had to
 * run with its whole spec in front of it collects dozens of requests, and the PNG of that is a
 * strip thousands of pixels tall that nobody can read. The `.mmd` renders fine in a viewer that
 * can scroll.
 */
const MAX_RENDERED_BLOCKS = 25;

/**
 * Entry points of the chains that say nothing about a use-case: the session check Quarkus runs
 * before every request, and the work the application does while it starts. Recorded and kept -
 * they are simply left out of the combined diagram and of the rendering.
 */
const INFRASTRUCTURE_ENTRY_POINT =
  /^(SessionAuthenticationMechanism|SessionService|CurrentUser|BootstrapAdminService|BackupService_applyRetention)_/;

const PACKAGED_APP = join(PROJECT_DIR, 'target', 'quarkus-app', 'quarkus-run.jar');
const RENDER_SCRIPT = resolve(PROJECT_DIR, '..', 'java-flow', 'render-diagrams.sh');

const only = argumentValue('--only');
const renderOnly = process.argv.includes('--render-only');
const render = renderOnly || process.argv.includes('--render');
const renderChains = process.argv.includes('--render-chains');

/** The setup project runs before every spec, so its own flows are recorded once, on their own. */
const SETUP_USE_CASE = {
  spec: 'auth.setup.ts',
  title: 'first start forces the administrator to pick a new password',
  project: 'setup',
};

main();

function main() {
  if (renderOnly) {
    renderAndIndex(readManifest());
    return;
  }
  if (!existsSync(PACKAGED_APP)) {
    fail(
      `${PACKAGED_APP} is missing. Build the application with the recorder switched on first:\n` +
        '  mvn package -DskipTests -Dcdi-flow.enabled=true',
    );
  }

  // Numbered from the whole suite before anything is filtered out, so --only leaves the
  // numbering of the other use-cases alone.
  const all = [SETUP_USE_CASE, ...listTests()].map((useCase, index) => {
    const number = String(index + 1).padStart(2, '0');
    return {
      ...useCase,
      number,
      directory: join(FLOW_DIR, specSlug(useCase.spec), `${number}-${slug(useCase.title)}`),
    };
  });
  const useCases = all.filter(
    (useCase) => !only || useCase.title.includes(only) || useCase.spec.includes(only),
  );
  if (useCases.length === 0) {
    fail(only ? `no use-case matches '${only}'` : 'the suite lists no tests');
  }

  console.log(`recording ${useCases.length} of ${all.length} use-case(s) into ${FLOW_DIR}\n`);
  if (!only) {
    rmSync(FLOW_DIR, { recursive: true, force: true });
  }

  const recorded = [];
  for (const useCase of useCases) {
    console.log(`[${useCase.number}/${all.length}] ${useCase.spec} - ${useCase.title}`);
    recorded.push(recordUseCase(useCase));
  }

  const failed = recorded.filter((useCase) => !useCase.passed);
  console.log(
    `${recorded.length} use-case(s) recorded, ` +
      `${recorded.reduce((sum, useCase) => sum + useCase.diagrams.length, 0)} diagrams kept` +
      (failed.length === 0 ? '' : `, ${failed.length} incomplete`),
  );
  failed.forEach((useCase) => console.log(`  incomplete: ${useCase.spec} - ${useCase.title}`));

  renderAndIndex(mergedManifest(recorded));
}

/**
 * Persists what was recorded before the slow part starts, renders it, and writes the indexes
 * afterwards - so a rendered PNG can be linked beside the diagram it came from, and an
 * interrupted rendering can be finished with --render-only.
 */
function renderAndIndex(manifest) {
  writeManifest(manifest);
  if (render) {
    renderToPng(manifest);
  }
  for (const useCase of manifest) {
    writeFileSync(join(FLOW_DIR, useCase.directory, 'README.md'), useCaseIndex(useCase));
  }
  writeFileSync(join(FLOW_DIR, 'README.md'), overallIndex(manifest));
  writeFileSync(join(FLOW_DIR, DOCUMENT), useCaseDocument(manifest));
}

/**
 * One document with every use-case in it: what it does, and its diagram inline so the file reads
 * on its own in anything that renders Mermaid.
 *
 * A combined diagram too large to render as an image is too large to inline as well - those are
 * linked instead, with the reason.
 */
function useCaseDocument(manifest) {
  const lines = [
    '# What each use-case does',
    '',
    'The 26 use-cases of the application, as the end-to-end suite drives them, each with the',
    'sequence diagram cdi-flow recorded while it ran. One block per request, in the order the',
    'application handled them; the blocks are the recorded chains, unchanged.',
    '',
    'Generated by `node e2e/scripts/record-flows.mjs` - the prose lives in',
    '`e2e/scripts/use-case-descriptions.mjs`, everything else is recorded. The per-chain diagrams',
    'each use-case is made of are in the directory linked from its heading.',
    '',
  ];
  for (const useCase of manifest) {
    const blocks = blocksOfCombined(useCase);
    lines.push(
      `## ${Number(useCase.number)}. ${useCase.title}`,
      '',
      descriptionOf(useCase),
      '',
      `Driven by \`e2e/tests/${useCase.spec}\`, ${outcomeOf(useCase)}. ` +
        `${blocks} request(s), ${useCase.diagrams.length} distinct chain(s) of ` +
        `${totalOf(useCase.diagrams)} recorded — [all of them](${useCase.directory}/README.md).`,
      '',
    );
    if (blocks === 0) {
      lines.push('No application chain was recorded for this use-case.', '');
      continue;
    }
    if (blocks > MAX_RENDERED_BLOCKS) {
      lines.push(
        `Its ${blocks} requests are too many to inline here: ` +
          `[\`${COMBINED}\`](${useCase.directory}/${COMBINED}).`,
        '',
      );
      continue;
    }
    lines.push(
      '```mermaid',
      readFileSync(join(FLOW_DIR, useCase.directory, COMBINED), 'utf8').trimEnd(),
      '```',
      '',
    );
  }
  return `${lines.join('\n')}\n`;
}

function descriptionOf(useCase) {
  return (
    USE_CASE_DESCRIPTIONS[`${useCase.spec}#${useCase.title}`] ??
    '_No description yet — add one to `e2e/scripts/use-case-descriptions.mjs`._'
  );
}

/**
 * Records one use-case, and gives a test that needs the data of its predecessors a second chance
 * with the whole spec in front of it.
 */
function recordUseCase(useCase) {
  const record = (wholeSpec) => {
    rmSync(useCase.directory, { recursive: true, force: true });
    mkdirSync(useCase.directory, { recursive: true });
    const passed = runUseCase(useCase, wholeSpec);
    // Before the duplicates go: the combined diagram is the story of the run, and a request the
    // test made twice belongs in it twice.
    const requests = stitchUseCaseDiagram(useCase);
    const diagrams = collapseIdenticalDiagrams(useCase.directory);
    console.log(
      `           ${passed ? 'passed' : 'FAILED'}${wholeSpec ? ' (whole spec)' : ''}, ` +
        `${diagrams.length} distinct flow(s) of ${totalOf(diagrams)} recorded, ` +
        `${requests} in the combined diagram`,
    );
    return {
      ...useCase,
      directory: relativeToFlowDir(useCase.directory),
      passed,
      wholeSpec,
      diagrams,
    };
  };

  let result = record(false);
  if (!result.passed) {
    console.log('           retrying with the whole spec, for the data it left behind');
    result = record(true);
  }
  console.log('');
  return result;
}

/** Asks Playwright itself which tests exist, rather than parsing the specs. */
function listTests() {
  const listing = execFileSync(
    'npx',
    ['playwright', 'test', '--list', '--reporter=json', '--project=chromium'],
    {
      cwd: E2E_DIR,
      encoding: 'utf8',
      maxBuffer: 32 * 1024 * 1024,
      stdio: ['ignore', 'pipe', 'pipe'],
    },
  );
  const tests = [];
  collectTests(JSON.parse(listing).suites ?? [], tests);
  // The listing includes the setup project the specs depend on; it is recorded on its own.
  return tests.filter((test) => test.spec !== SETUP_USE_CASE.spec);
}

function collectTests(suites, collected, file) {
  for (const suite of suites) {
    const specFile = suite.file ?? file;
    for (const spec of suite.specs ?? []) {
      collected.push({ spec: spec.file ?? specFile, title: spec.title, project: 'chromium' });
    }
    collectTests(suite.suites ?? [], collected, specFile);
  }
}

/**
 * Runs one test with the recorder pointed at its directory. Playwright starts the packaged
 * application itself, on a database directory it creates fresh for every run, so no use-case
 * inherits the data of the one before it - which is also why a test that expects such data has
 * to be run with {@code wholeSpec}, its predecessors included.
 */
function runUseCase(useCase, wholeSpec) {
  const reportDirectory = mkdtempSync(join(tmpdir(), 'small-crm-flow-report-'));
  try {
    const result = spawnSync(
      'npx',
      [
        'playwright',
        'test',
        useCase.spec,
        '--project',
        useCase.project,
        // Playwright matches --grep against "[project] › file:line › title", so only the end of
        // the title can be anchored.
        ...(wholeSpec ? [] : ['--grep', `${escapeForRegExp(useCase.title)}$`]),
        '--reporter=line',
      ],
      {
        cwd: E2E_DIR,
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'pipe'],
        env: {
          ...process.env,
          CDI_FLOW_ENABLED: 'true',
          CDI_FLOW_OUTPUT_DIRECTORY: useCase.directory,
          PLAYWRIGHT_HTML_REPORT: reportDirectory,
        },
      },
    );
    if (result.status !== 0) {
      const output = `${result.stdout ?? ''}${result.stderr ?? ''}`.trimEnd();
      console.log(output.split('\n').slice(-12).join('\n'));
    }
    return result.status === 0;
  } finally {
    rmSync(reportDirectory, { recursive: true, force: true });
  }
}

/**
 * Writes the one diagram that reads as the use-case: every application chain the run produced, in
 * the order it happened, each in a block of its own.
 *
 * cdi-flow cannot record this itself, and nothing here is invented either - the blocks are the
 * recorded chains, unchanged apart from their own note line. A flow ends when its outermost call
 * returns, and each request is a separate outermost call on a separate thread, so one request per
 * block is as far as a recording goes.
 *
 * @return how many chains went in
 */
function stitchUseCaseDiagram(useCase) {
  // In the order the application handled them, which is the order of the start-stamp in the name.
  const chains = applicationDiagramsOf(readdirSync(useCase.directory))
    .sort((left, right) => startedAt(left).localeCompare(startedAt(right)))
    .map((file) => ({
      file,
      ...parseDiagram(readFileSync(join(useCase.directory, file), 'utf8')),
    }));
  if (chains.length === 0) {
    return 0;
  }

  const participants = ['    participant Caller as caller'];
  for (const chain of chains) {
    for (const participant of chain.participants) {
      if (!participants.includes(participant)) {
        participants.push(participant);
      }
    }
  }

  const lines = ['sequenceDiagram', '    autonumber', ...participants];
  for (const chain of chains) {
    lines.push('    rect rgb(244, 244, 244)');
    lines.push(
      `        Note over Caller,${chain.lastParticipant}: ` +
        `${entryPointOf(chain.file)}${chain.summary ? ` — ${chain.summary}` : ''}`,
    );
    lines.push(...chain.body.map((line) => `    ${line}`));
    lines.push('    end');
  }
  writeFileSync(join(useCase.directory, COMBINED), `${lines.join('\n')}\n`);
  return chains.length;
}

/**
 * Splits a recorded diagram into the parts the combined one needs: the participant declarations,
 * and the arrows without the note that carries the timestamps of that single chain.
 */
function parseDiagram(diagram) {
  const participants = [];
  const body = [];
  let summary = '';
  for (const line of diagram.split('\n')) {
    const trimmed = line.trim();
    if (trimmed === 'sequenceDiagram' || trimmed === 'autonumber' || trimmed === '') {
      continue;
    }
    if (trimmed.startsWith('participant ')) {
      // The caller lane is declared once, for the whole combined diagram.
      if (trimmed !== 'participant Caller as caller') {
        participants.push(line);
      }
      continue;
    }
    if (trimmed.startsWith('Note over Caller,')) {
      // "<start> - <end><br/>10.2 ms | thread executor-thread-2" - the duration is worth keeping.
      summary = trimmed.split('<br/>')[1] ?? '';
      continue;
    }
    body.push(line);
  }
  const last = participants[participants.length - 1];
  return {
    participants,
    body,
    summary,
    lastParticipant: last ? last.trim().replace(/^participant /, '') : 'Caller',
  };
}

/** The start-stamp cdi-flow puts in the file name: `<Type>_<method>_<start>_<end>.mmd`. */
function startedAt(file) {
  return file.split('_')[2] ?? file;
}

/**
 * The chains worth looking at: what the application does, without the session check Quarkus runs
 * before every single request and the work it does while it starts. Those are the same three
 * shapes in every use-case, and they have their own use-case in `auth/`.
 */
function applicationDiagramsOf(files) {
  return files.filter(
    (file) => file.endsWith('.mmd') && file !== COMBINED && !INFRASTRUCTURE_ENTRY_POINT.test(file),
  );
}

/**
 * Keeps one file per distinct call chain. Two chains count as the same when their diagrams
 * differ only in the timestamps and durations - the note above the first arrow and the
 * bracketed millisecond on every return.
 */
function collapseIdenticalDiagrams(directory) {
  const kept = new Map();
  for (const name of readdirSync(directory)
    .filter((file) => file.endsWith('.mmd') && file !== COMBINED)
    .sort()) {
    const path = join(directory, name);
    const shape = createHash('sha256')
      .update(
        readFileSync(path, 'utf8')
          .replace(/^\s*Note over .*$/gm, '')
          .replace(/\[\d+\.\d+ ms\]/g, '[]')
          .replace(/took \d+\.\d+ ms/g, 'took'),
      )
      .digest('hex');
    const alreadyKept = kept.get(shape);
    if (alreadyKept) {
      alreadyKept.occurrences += 1;
      unlinkSync(path);
    } else {
      kept.set(shape, {
        file: name,
        entryPoint: entryPointOf(name),
        application: applicationDiagramsOf([name]).length === 1,
        occurrences: 1,
      });
    }
  }
  return [...kept.values()].sort((left, right) => left.file.localeCompare(right.file));
}

/** `ContactResource_list_20260804-210452454_20260804-210452454.mmd` -> `ContactResource.list` */
function entryPointOf(fileName) {
  const [type, method] = fileName.split('_');
  return method ? `${type}.${method}` : fileName;
}

function useCaseIndex(useCase) {
  const { diagrams } = useCase;
  const lines = [
    `# ${useCase.title}`,
    '',
    `Recorded from \`e2e/tests/${useCase.spec}\`, ${outcomeOf(useCase)}.`,
    '',
  ];
  const blocks = blocksOfCombined(useCase);
  if (blocks > 0) {
    lines.push(
      `**The use-case as one diagram: [\`${COMBINED}\`](${COMBINED})${imageLink(useCase, COMBINED)}**`,
      `— the ${blocks} application chains below, in the order the application handled them, one`,
      'block per request.',
      ...(blocks > MAX_RENDERED_BLOCKS
        ? [
            '',
            `Not rendered to PNG: ${blocks} requests make an image thousands of pixels tall. The`,
            '`.mmd` above renders in any Mermaid viewer that can scroll.',
          ]
        : []),
      '',
    );
  }
  lines.push(
    `${diagrams.length} distinct call chain(s), out of ${totalOf(diagrams)} recorded:`,
    '',
    '| Entry point | Diagram | Recorded |',
    '|---|---|---|',
  );
  for (const diagram of diagrams) {
    lines.push(
      `| \`${diagram.entryPoint}\` | [\`${diagram.file}\`](${diagram.file})` +
        `${imageLink(useCase, diagram.file)} | ${diagram.occurrences}× |`,
    );
  }
  lines.push(
    '',
    'Only the combined diagram above is rendered to PNG; `--render-chains` renders these single',
    'chains as well, which is worth doing locally and not worth committing. The chains that are',
    'missing from it — `SessionAuthenticationMechanism`, `SessionService`, `CurrentUser`, and the',
    'startup work of `BootstrapAdminService` and `BackupService.applyRetention` — are the session',
    'check Quarkus runs before every request and what the application does while it starts: the',
    'same shapes in every use-case, recorded here like everything else.',
  );
  return `${lines.join('\n')}\n`;
}

function imageLink(useCase, diagramFile) {
  const png = diagramFile.replace(/\.mmd$/, '.png');
  return existsSync(join(FLOW_DIR, useCase.directory, png)) ? ` · [PNG](${png})` : '';
}

function overallIndex(recorded) {
  const lines = [
    '# Recorded flows',
    '',
    'One sequence diagram per call chain, recorded by cdi-flow while the end-to-end suite drove',
    'each use-case of the application. Written by `node e2e/scripts/record-flows.mjs --render`',
    'against a build made with `-Dcdi-flow.enabled=true`; nothing here is hand-drawn.',
    '',
    'A flow ends when its outermost call returns, and each request is its own outermost call on its',
    'own thread, so a use-case is a handful of chains rather than one. `use-case.mmd` stitches them',
    'back together in the order the application handled them, one block per request - the blocks are',
    'the recorded chains, unchanged.',
    '',
    'Every use-case starts from an empty installation, so the first blocks of each combined diagram',
    'are the sign-in the suite does before the test itself: `AuthResource.login`, the forced',
    "`changePassword`, `me`, and the overview that follows. The use-case's own work comes after it.",
    '',
    'Identical chains are collapsed to one file - the `Flows` column counts how many chains were',
    "recorded, `Diagrams` how many distinct ones remain. Only public methods of this application's",
    'own beans appear: a method a bean calls on itself never leaves the instance, so no interceptor',
    'sees it, and the static Panache calls of the entities are not bean calls at all.',
    '',
    '| # | Use-case | Spec | Flows | Diagrams | Run |',
    '|---|---|---|---|---|---|',
  ];
  for (const useCase of recorded) {
    const combined = existsSync(join(FLOW_DIR, useCase.directory, COMBINED))
      ? `[${useCase.title}](${useCase.directory}/${COMBINED})`
      : useCase.title;
    lines.push(
      `| ${useCase.number} | ${combined} | \`${useCase.spec}\` | ` +
        `${totalOf(useCase.diagrams)} | ` +
        `[${useCase.diagrams.length} kept](${useCase.directory}/README.md) | ` +
        `${outcomeOf(useCase)} |`,
    );
  }
  return `${lines.join('\n')}\n`;
}

/** What the run of a use-case is worth, in the words the two indexes both use. */
function outcomeOf(useCase) {
  if (useCase.passed) {
    return useCase.wholeSpec ? 'passed, run with its whole spec in front of it' : 'passed';
  }
  return (
    'recorded up to the assertion it failed on — it expects data that a spec ' +
    'earlier in the suite leaves behind'
  );
}

/** Merges what was just recorded into what earlier runs recorded, so `--only` keeps the index. */
function mergedManifest(recorded) {
  const merged = new Map(
    readManifest().map((useCase) => [`${useCase.spec}#${useCase.title}`, useCase]),
  );
  for (const useCase of recorded) {
    merged.set(`${useCase.spec}#${useCase.title}`, useCase);
  }
  return [...merged.values()]
    .filter((useCase) => existsSync(join(FLOW_DIR, useCase.directory)))
    .sort((left, right) => left.number.localeCompare(right.number));
}

function readManifest() {
  const path = join(FLOW_DIR, MANIFEST);
  if (!existsSync(path)) {
    if (renderOnly) {
      fail(`${path} is not there - there is nothing recorded to render`);
    }
    return [];
  }
  return JSON.parse(readFileSync(path, 'utf8'));
}

function writeManifest(manifest) {
  writeFileSync(join(FLOW_DIR, MANIFEST), `${JSON.stringify(manifest, undefined, 2)}\n`);
}

/**
 * Renders one use-case per container run, and skips the use-cases whose images are there already -
 * a headless browser per diagram is what makes this by far the slowest part.
 *
 * The renderer of cdi-flow takes a directory and renders everything below it, so what is to be
 * rendered is staged in a directory of its own: the combined diagram and the application chains,
 * not the session check of every single request.
 */
function renderToPng(manifest) {
  if (!existsSync(RENDER_SCRIPT)) {
    console.log(`\nnot rendering: ${RENDER_SCRIPT} is not there`);
    return;
  }
  const outstanding = manifest.filter((useCase) => missingImagesOf(useCase).length > 0);
  const total = outstanding.reduce((sum, useCase) => sum + missingImagesOf(useCase).length, 0);
  console.log(
    `\nrendering ${total} diagram(s) of ${outstanding.length} use-case(s) to PNG` +
      `${outstanding.length < manifest.length ? ', the rest is rendered already' : ''} ...`,
  );
  for (const useCase of outstanding) {
    renderUseCase(useCase);
    const missing = missingImagesOf(useCase);
    if (missing.length > 0) {
      console.log(
        `  ${useCase.directory}: ${missing.length} diagram(s) not rendered; ` +
          'the .mmd files are there regardless, and --render-only picks this up again',
      );
    }
  }
}

function renderUseCase(useCase) {
  const directory = join(FLOW_DIR, useCase.directory);
  const staging = mkdtempSync(join(tmpdir(), 'small-crm-flow-render-'));
  try {
    for (const image of missingImagesOf(useCase)) {
      const diagram = image.replace(/\.png$/, '.mmd');
      copyFileSync(diagram, join(staging, diagram.slice(directory.length + 1)));
    }
    spawnSync(RENDER_SCRIPT, [staging], { stdio: 'inherit' });
    for (const image of readdirSync(staging).filter((file) => file.endsWith('.png'))) {
      copyFileSync(join(staging, image), join(directory, image));
    }
  } finally {
    rmSync(staging, { recursive: true, force: true });
  }
}

/**
 * The images that should exist for a use-case and do not: what is left to render.
 *
 * The combined diagram is the one worth having as an image, so it is the only one rendered by
 * default - a PNG of every single chain is 30 MB of near-duplicates. `--render-chains` adds the
 * application chains for a local look at them.
 */
function missingImagesOf(useCase) {
  const directory = join(FLOW_DIR, useCase.directory);
  return [
    ...(isRenderableCombined(useCase) ? [COMBINED] : []),
    ...(renderChains
      ? useCase.diagrams.filter((diagram) => diagram.application).map((diagram) => diagram.file)
      : []),
  ]
    .map((diagram) => join(directory, diagram.replace(/\.mmd$/, '.png')))
    .filter((image) => !existsSync(image));
}

function isRenderableCombined(useCase) {
  return blocksOfCombined(useCase) > 0 && blocksOfCombined(useCase) <= MAX_RENDERED_BLOCKS;
}

/** How many requests the combined diagram of a use-case holds; 0 when there is none. */
function blocksOfCombined(useCase) {
  const path = join(FLOW_DIR, useCase.directory, COMBINED);
  return existsSync(path) ? (readFileSync(path, 'utf8').match(/rect rgb/g) ?? []).length : 0;
}

function relativeToFlowDir(directory) {
  return directory.slice(FLOW_DIR.length + 1);
}

function totalOf(diagrams) {
  return diagrams.reduce((sum, diagram) => sum + diagram.occurrences, 0);
}

function specSlug(spec) {
  return slug(spec.replace(/\.(spec|setup)\.ts$/, ''));
}

function slug(text) {
  return text
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 70);
}

function escapeForRegExp(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function argumentValue(name) {
  const index = process.argv.indexOf(name);
  return index >= 0 ? process.argv[index + 1] : undefined;
}

function fail(message) {
  console.error(message);
  process.exit(1);
}
