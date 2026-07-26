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
 * Builds the distributions on a machine with nothing installed.
 *
 * Reached through ./build.sh or build.cmd, which fetch the Node this script needs and then hand
 * over. Everything else — the JDK, Maven, pnpm — is fetched here into .build-tools/ and used only
 * from there: no JAVA_HOME is read, no `mvn` on the PATH is called, nothing outside the project
 * folder is written or changed. Deleting .build-tools/ undoes the whole thing.
 *
 *   ./build.sh                    all three platforms
 *   ./build.sh linux-x64          just one
 *
 * Every download is checked against the checksum its publisher announces, and a file that does
 * not match stops the build rather than being used.
 */

import { existsSync, mkdirSync, readFileSync } from "node:fs";
import { delimiter, dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import {
  downloadVerified,
  hostPlatform,
  resolveTemurin,
  run,
  runtimeHome,
  unpack,
} from "./toolchain.mjs";

const PACKAGING = dirname(fileURLToPath(import.meta.url));
const PROJECT = resolve(PACKAGING, "..");
const TOOLS = join(PROJECT, ".build-tools");
const CACHE = join(TOOLS, "cache");
const BIN = join(TOOLS, "bin");

/** Java feature release to build with; the same one the packages carry and pom.xml targets. */
const JAVA_VERSION = "25";

/**
 * Maven is pinned rather than resolved, because Apache publishes no "what is current" endpoint.
 * Bumping it is this line plus nothing else — the checksum is read from Apache at download time,
 * never written down here, so a stale hash cannot outlive the version it belonged to.
 */
const MAVEN_VERSION = "3.9.16";

function log(message = "") {
  console.log(message);
}

async function fetchText(url) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`${url} answered ${response.status}`);
  }
  return await response.text();
}

async function installJdk({ os, arch }) {
  const jdk = await resolveTemurin({
    javaVersion: JAVA_VERSION,
    os,
    arch,
    imageType: "jdk",
  });
  log(`  Java ${jdk.version}`);
  const archive = await downloadVerified(jdk, CACHE, log);
  // No javaHome to pass: this *is* the JDK, so there is no `jar` yet and unpack falls back to
  // tar, which is what makes the very first step work on a machine with nothing on it.
  return runtimeHome(unpack(archive, join(TOOLS, "jdk")));
}

async function installMaven() {
  const base = `https://downloads.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries`;
  const name = `apache-maven-${MAVEN_VERSION}-bin.tar.gz`;
  // Apache publishes the hash beside the file, sometimes bare and sometimes "hash  filename".
  const published = await fetchText(`${base}/${name}.sha512`);
  log(`  Maven ${MAVEN_VERSION}`);
  const archive = await downloadVerified(
    {
      name,
      link: `${base}/${name}`,
      checksum: published.trim().split(/\s+/)[0],
      algorithm: "sha512",
    },
    CACHE,
    log,
  );
  return join(unpack(archive, join(TOOLS, "maven")), `apache-maven-${MAVEN_VERSION}`);
}

/**
 * Puts a pnpm on the PATH for Quinoa to call.
 *
 * Corepack ships with Node and reads the exact version out of the packageManager field in
 * package.json, so this pins itself to whatever the lockfiles were written with. The shims go
 * into .build-tools/bin rather than next to the Node binary, so this works the same whether
 * Node was bootstrapped or already there, and never writes outside the project.
 */
function installPnpm(env) {
  mkdirSync(BIN, { recursive: true });
  try {
    run("corepack", ["enable", "pnpm", "--install-directory", BIN], { env });
  } catch (cause) {
    throw new Error(
      "Could not set up pnpm: this Node has no usable corepack. Run ./build.sh, which " +
        "fetches a Node that has one, or install pnpm 11 yourself and put it on the PATH.",
      { cause },
    );
  }
  const declared = JSON.parse(
    readFileSync(join(PROJECT, "src", "main", "webui", "package.json"), "utf8"),
  ).packageManager;
  log(`  ${declared ?? "pnpm"}`);
}

function projectVersion() {
  const pom = readFileSync(join(PROJECT, "pom.xml"), "utf8");
  const match = pom.match(
    /<artifactId>small-crm<\/artifactId>\s*<version>([^<]+)<\/version>/,
  );
  return match ? match[1] : "unknown";
}

const targets = process.argv.slice(2);
const host = hostPlatform();

log(`Small CRM ${projectVersion()} — building from nothing`);
log(`Build host: ${host.os}/${host.arch}`);
log("");
log("Toolchain");

const javaHome = await installJdk(host);
const mavenHome = await installMaven();

// The child processes see this and nothing of the ambient toolchain: our bins come first on the
// PATH and JAVA_HOME points inside .build-tools, so a different Java or Maven already installed
// on this machine cannot change what comes out.
const env = {
  ...process.env,
  JAVA_HOME: javaHome,
  MAVEN_HOME: mavenHome,
  PATH: [
    BIN,
    join(javaHome, "bin"),
    join(mavenHome, "bin"),
    dirname(process.execPath),
    process.env.PATH ?? "",
  ].join(delimiter),
  // Corepack would otherwise stop and ask before fetching pnpm, which a build script cannot
  // answer. The version it fetches is the pinned one from package.json either way.
  COREPACK_ENABLE_DOWNLOAD_PROMPT: "0",
};

installPnpm(env);

const mvn = join(mavenHome, "bin", process.platform === "win32" ? "mvn.cmd" : "mvn");
log("");
log("Building the application");
run(mvn, ["--batch-mode", "package"], {
  cwd: PROJECT,
  env,
  stdio: "inherit",
});

log("");
log("Building the packages");
run(process.execPath, [join(PACKAGING, "build-distributions.mjs"), ...targets], {
  cwd: PROJECT,
  env,
  stdio: "inherit",
});

if (!existsSync(join(PROJECT, "target", "dist"))) {
  throw new Error("The build finished but target/dist is not there");
}
