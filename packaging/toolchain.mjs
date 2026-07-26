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
 * Fetching and unpacking toolchains, shared by bootstrap-build.mjs (which downloads what the
 * build itself needs) and build-distributions.mjs (which downloads the runtimes that go into
 * the packages). Both do the same three things — ask a publisher what is current, fetch it,
 * refuse to use it unless it matches the published checksum — so they do them the same way.
 */

import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
import {
  existsSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  rmSync,
  statSync,
  writeFileSync,
} from "node:fs";
import { join } from "node:path";

export function run(command, args, options = {}) {
  return execFileSync(command, args, { encoding: "utf8", ...options });
}

export function digest(file, algorithm) {
  return createHash(algorithm).update(readFileSync(file)).digest("hex");
}

/** The platform this script is running on, named the way Adoptium names platforms. */
export function hostPlatform() {
  const os = { darwin: "mac", linux: "linux", win32: "windows" }[process.platform];
  const arch = { arm64: "aarch64", x64: "x64" }[process.arch];
  if (!os || !arch) {
    throw new Error(
      `Unsupported build host: ${process.platform}/${process.arch}. ` +
        "Build on macOS (Apple silicon), Linux x64 or Windows x64.",
    );
  }
  return { os, arch };
}

/**
 * Asks Adoptium which Temurin build is current for a platform, and where to get it.
 *
 * imageType is "jdk" to build with or "jre" to ship; the JRE is about half the size, which is
 * why the packages get that one and only the build host needs the full kit.
 */
export async function resolveTemurin({ javaVersion, os, arch, imageType }) {
  const url =
    `https://api.adoptium.net/v3/assets/latest/${javaVersion}/hotspot` +
    `?architecture=${arch}&image_type=${imageType}&os=${os}&vendor=eclipse`;
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Adoptium said ${response.status} for ${os}/${arch}`);
  }
  const [asset] = await response.json();
  if (!asset) {
    throw new Error(
      `Adoptium has no Java ${javaVersion} ${imageType} for ${os}/${arch}`,
    );
  }
  // The response also carries an "installer" for some platforms; the package is the plain
  // archive, which is the only one that can be unpacked into a folder we control.
  return {
    version: asset.version.semver,
    name: asset.binary.package.name,
    link: asset.binary.package.link,
    checksum: asset.binary.package.checksum,
    algorithm: "sha256",
  };
}

/**
 * Fetches a file into the cache unless a good copy is already there, and verifies it against
 * the checksum its publisher announced. A file that does not match is deleted rather than
 * used: a corrupt or substituted archive should stop the build, not end up inside a package
 * somebody else runs.
 */
export async function downloadVerified(artifact, cache, log = () => {}) {
  mkdirSync(cache, { recursive: true });
  const file = join(cache, artifact.name);
  const algorithm = artifact.algorithm ?? "sha256";
  if (existsSync(file) && digest(file, algorithm) === artifact.checksum) {
    return file;
  }
  log(`  downloading ${artifact.name}`);
  const response = await fetch(artifact.link);
  if (!response.ok) {
    throw new Error(
      `Downloading ${artifact.name} failed with ${response.status}`,
    );
  }
  writeFileSync(file, Buffer.from(await response.arrayBuffer()));
  const actual = digest(file, algorithm);
  if (actual !== artifact.checksum) {
    rmSync(file);
    throw new Error(
      `${artifact.name} does not match its published ${algorithm}; it was not used`,
    );
  }
  return file;
}

/**
 * Locates a `jar` command, or null if there is none to be had.
 *
 * `jar` stands in for `zip` and `unzip`, which are absent from plenty of minimal Linux images
 * and have nothing to do with Java. A JDK is around in almost every path that gets here — the
 * bootstrap fetches one, and building without the bootstrap needs one for Maven anyway — but
 * not quite all of them, which is why this can come back empty rather than guessing.
 */
export function jarExecutable(javaHome = process.env.JAVA_HOME) {
  const executable = process.platform === "win32" ? "jar.exe" : "jar";
  if (javaHome) {
    const candidate = join(javaHome, "bin", executable);
    if (existsSync(candidate)) {
      return candidate;
    }
  }
  try {
    run(executable, ["--version"], { stdio: "ignore" });
    return executable;
  } catch {
    return null;
  }
}

/**
 * Unpacks an archive into a directory, once. The marker file makes a second run cheap, which
 * matters because a full build fetches three runtimes and nobody wants to pay for unpacking
 * them again on every attempt.
 *
 * Zip needs a word of explanation. `jar` is the first choice because it is the one unpacker
 * guaranteed to be present once there is a JDK. It is not always: the very first thing the
 * bootstrap unpacks on Windows is the JDK itself, and `jar` is inside it. There, tar takes
 * over — the tar that Windows and macOS ship is bsdtar, which reads zip perfectly well. Only
 * GNU tar on Linux cannot, and on Linux the zip in question is a Windows runtime being packed
 * for someone else, by which point the JDK has long since arrived.
 */
export function unpack(archive, into, javaHome) {
  if (existsSync(join(into, "unpacked.ok"))) {
    return into;
  }
  rmSync(into, { recursive: true, force: true });
  mkdirSync(into, { recursive: true });
  if (archive.endsWith(".zip")) {
    const jar = jarExecutable(javaHome);
    if (jar) {
      run(jar, ["--extract", "--file", archive], { cwd: into });
    } else {
      run("tar", ["-xf", archive, "-C", into]);
    }
  } else {
    run("tar", ["-xzf", archive, "-C", into]);
  }
  writeFileSync(join(into, "unpacked.ok"), "");
  return into;
}

/**
 * The directory holding bin/ and lib/ inside an unpacked runtime, which is one level down
 * from where it was unpacked and one level further on macOS, where the archive carries the
 * whole application-bundle layout.
 */
export function runtimeHome(unpacked) {
  const [root] = readdirSync(unpacked)
    .map((entry) => join(unpacked, entry))
    .filter((entry) => statSync(entry).isDirectory());
  if (!root) {
    throw new Error(`Nothing was unpacked into ${unpacked}`);
  }
  const bundled = join(root, "Contents", "Home");
  return existsSync(bundled) ? bundled : root;
}
