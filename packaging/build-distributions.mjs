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
 * Builds a self-contained package of Small CRM for macOS, Linux and Windows.
 *
 *   mvn package && node packaging/build-distributions.mjs
 *   node packaging/build-distributions.mjs macos-aarch64      (just one)
 *
 * "Self-contained" means the person receiving it needs nothing installed: each archive
 * carries its own Java runtime, so unpacking it and running the start script is the whole
 * procedure. The data, backups and logs live inside the same folder, which makes the folder
 * the installation — copy it to another machine of the same kind and it carries on where it
 * left off.
 *
 * All three are built from this one host, by bundling the Eclipse Temurin JRE that Adoptium
 * publishes for each platform. jlink would produce a smaller runtime, but it cannot cross-build:
 * given another platform's modules it still writes this host's launcher, so the Linux package
 * came out with a macOS `java` in it. An official per-platform JRE is a genuinely native
 * runtime and needs no build machine of that kind.
 *
 * Downloads are cached in target/jdk-cache and verified against the checksum Adoptium publishes;
 * a file that does not match is deleted rather than used.
 */

import {
  chmodSync,
  cpSync,
  existsSync,
  mkdirSync,
  readFileSync,
  rmSync,
  statSync,
  writeFileSync,
} from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import {
  downloadVerified,
  jarExecutable,
  resolveTemurin,
  run,
  runtimeHome,
  unpack,
} from "./toolchain.mjs";

const PACKAGING = dirname(fileURLToPath(import.meta.url));
const PROJECT = resolve(PACKAGING, "..");
const QUARKUS_APP = join(PROJECT, "target", "quarkus-app");
const CACHE = join(PROJECT, "target", "jdk-cache");
const WORK = join(PROJECT, "target", "dist-work");
const OUTPUT = join(PROJECT, "target", "dist");

/** Java feature release to bundle; the same one the project is built with. */
const JAVA_VERSION = "25";

const TARGETS = [
  {
    id: "macos-aarch64",
    label: "macOS (Apple silicon)",
    api: { os: "mac", arch: "aarch64" },
    archive: "tar.gz",
    launcher: "start.sh",
  },
  {
    id: "linux-x64",
    label: "Linux (Intel/AMD 64-bit)",
    api: { os: "linux", arch: "x64" },
    archive: "tar.gz",
    launcher: "start.sh",
  },
  {
    id: "windows-x64",
    label: "Windows (Intel/AMD 64-bit)",
    api: { os: "windows", arch: "x64" },
    archive: "zip",
    launcher: "start.cmd",
  },
];

function projectVersion() {
  const pom = readFileSync(join(PROJECT, "pom.xml"), "utf8");
  const match = pom.match(
    /<artifactId>small-crm<\/artifactId>\s*<version>([^<]+)<\/version>/,
  );
  if (!match) {
    throw new Error("Could not read the project version from pom.xml");
  }
  return match[1];
}

function readme(target, version, jreVersion) {
  const script = target.launcher;
  const windows = target.id.startsWith("windows");
  return `Small CRM ${version} — ${target.label}
${"=".repeat(60)}

Starting it
-----------

${windows ? "Double-click start.cmd." : `Open this folder in a terminal and run:\n\n    ./${script}`}

The first start takes a few seconds. Your browser opens by itself at
http://localhost:8080 once the application is ready.

The very first start prints a password in this window, under a heading that
reads "Small CRM first start". Sign in as "admin" with it and choose your own
password when asked.

If you close the window before reading it, it is also in the log:

    logs/small-crm.log

Look for the line beginning "password:". It stays in that file in plain text
until the log rotates, so once you have signed in and chosen your own password,
delete the log if other people can read this machine. There is no other copy of
it anywhere.

To stop it, close the window${windows ? "" : " or press Ctrl+C"}.

Nothing has to be installed. Java is inside this folder (runtime/), so this
does not touch or need any Java you may already have.

Your data
---------

Everything lives in this folder:

    data/      the database
    backup/    automatic backups, one per change, kept 14 days by default
    logs/      what the application recorded

This folder is the whole installation. Copy it to another ${target.label}
machine and it carries on exactly where it left off — but copy it while the
application is stopped, otherwise the database file may be caught mid-write.

If something goes wrong
-----------------------

Port 8080 already in use: set a different one before starting.

${
  windows
    ? "    set SMALLCRM_PORT=9000\n    start.cmd"
    : "    SMALLCRM_PORT=9000 ./" + script
}

Browser should not open by itself: set SMALLCRM_NO_BROWSER=1.

Everything else: look in logs/small-crm.log, or in the window itself.

What is inside
--------------

    app/       the application
    runtime/   Java ${jreVersion}, the Eclipse Temurin runtime for this platform
    ${script.padEnd(10)} the start script

Small CRM is Apache License 2.0 — see LICENSE. The bundled Java runtime is
GPLv2 with the Classpath Exception, as published by Eclipse Temurin.
`;
}

function assemble(target, version, jreHome, jreVersion) {
  const name = `small-crm-${version}-${target.id}`;
  const stage = join(WORK, name);
  console.log(`  assembling ${name}`);
  rmSync(stage, { recursive: true, force: true });
  mkdirSync(stage, { recursive: true });

  cpSync(QUARKUS_APP, join(stage, "app"), { recursive: true });
  // preserveTimestamps keeps the executable bits along with everything else.
  cpSync(jreHome, join(stage, "runtime"), {
    recursive: true,
    preserveTimestamps: true,
  });

  const launcher = join(stage, target.launcher);
  cpSync(join(PACKAGING, "templates", target.launcher), launcher);
  if (target.launcher.endsWith(".sh")) {
    chmodSync(launcher, 0o755);
  }
  cpSync(join(PROJECT, "LICENSE"), join(stage, "LICENSE"));
  writeFileSync(join(stage, "README.txt"), readme(target, version, jreVersion));

  // Shipped empty so the folder explains itself before the first start.
  for (const folder of ["data", "backup", "logs"]) {
    mkdirSync(join(stage, folder), { recursive: true });
    writeFileSync(
      join(stage, folder, ".keep"),
      `Small CRM writes the ${folder} here when it runs.\n`,
    );
  }
  return { name, stage };
}

function archive(target, name) {
  mkdirSync(OUTPUT, { recursive: true });
  if (target.archive === "zip") {
    const file = join(OUTPUT, `${name}.zip`);
    rmSync(file, { force: true });
    // `jar` rather than `zip`, which minimal Linux images do not have. --no-manifest keeps it a
    // plain archive instead of quietly turning it into a jar. The execute bit a zip cannot
    // carry anyway is no loss here: this archive only ever holds the Windows package, whose
    // launcher is a .cmd.
    const jar = jarExecutable();
    if (!jar) {
      throw new Error(
        "No `jar` found to write the Windows archive. Set JAVA_HOME to a JDK, or " +
          "use ./build.sh, which fetches one.",
      );
    }
    run(jar, ["--create", "--file", file, "--no-manifest", "-C", WORK, name]);
    return file;
  }
  const file = join(OUTPUT, `${name}.tar.gz`);
  rmSync(file, { force: true });
  // --no-xattrs keeps macOS from putting ._ files in an archive bound for Linux.
  run("tar", ["--no-xattrs", "-czf", file, name], { cwd: WORK });
  return file;
}

function megabytes(file) {
  return `${(statSync(file).size / 1024 / 1024).toFixed(0)} MB`;
}

const wanted = process.argv.slice(2);
const selected = wanted.length
  ? TARGETS.filter((target) => wanted.includes(target.id))
  : TARGETS;
if (selected.length === 0) {
  throw new Error(
    `Unknown target. Choose from: ${TARGETS.map((t) => t.id).join(", ")}`,
  );
}

if (!existsSync(join(QUARKUS_APP, "quarkus-run.jar"))) {
  throw new Error(`${QUARKUS_APP} is not there yet. Run "mvn package" first.`);
}

const version = projectVersion();
console.log(`Small CRM ${version}\n`);

rmSync(WORK, { recursive: true, force: true });
const built = [];
for (const target of selected) {
  console.log(`${target.label}`);
  const jre = await resolveTemurin({
    javaVersion: JAVA_VERSION,
    ...target.api,
    imageType: "jre",
  });
  const runtimeArchive = await downloadVerified(jre, CACHE, (message) =>
    console.log(message),
  );
  const jreHome = runtimeHome(
    unpack(runtimeArchive, join(CACHE, target.id), process.env.JAVA_HOME),
  );
  const { name } = assemble(target, version, jreHome, jre.version);
  const file = archive(target, name);
  built.push({ target, file });
  console.log("");
}
rmSync(WORK, { recursive: true, force: true });

console.log("\nBuilt:");
for (const { target, file } of built) {
  console.log(
    `  ${target.label.padEnd(28)} ${megabytes(file).padStart(7)}  ${file}`,
  );
}
