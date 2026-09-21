// Records and checks the environment fingerprint produced by `compose-version.sh`, so that a
// measurement run can skip the build/equivalence work without silently measuring
// artifacts that no longer match their sources.
import { execFileSync } from "node:child_process";
import { access, mkdir, readFile, readdir, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { gitState, hash, rootDirectory } from "../harness/browser/run-record.mjs";

const stampPath = resolve(rootDirectory, "build/verification.json");
const requiredArtifacts = [
  "build/compose-provenance.json",
  "build/ssr-html/tailwind-catalog.html",
  "build/web/react/dist/server-render.mjs",
  "workloads/jvm/src/main/resources/fixtures.json",
];

const [mode] = process.argv.slice(2);
const options = Object.fromEntries(process.argv.slice(3).map(argument => {
  const match = /^--([^=]+)=(.*)$/.exec(argument);
  if (!match) throw new Error(`Unknown argument: ${argument}`);
  return [match[1], match[2]];
}));

async function recursiveDirFingerprint(directory) {
  const entries = [];
  async function walk(dir) {
    let list = [];
    try { list = await readdir(dir, { withFileTypes: true }); } catch { return; }
    for (const item of list) {
      if (item.name === ".git" || item.name === "build" || item.name === ".gradle" || item.name === "node_modules") continue;
      const fullPath = resolve(dir, item.name);
      if (item.isDirectory()) {
        await walk(fullPath);
      } else if (item.isFile()) {
        const rel = fullPath.slice(directory.length + 1);
        try {
          entries.push(`${rel}:${hash(await readFile(fullPath))}`);
        } catch {}
      }
    }
  }
  await walk(directory);
  entries.sort();
  return hash(entries.join("\n"));
}

async function contentFingerprint(directory, listArguments) {
  let files = [];
  try {
    files = execFileSync("git", ["-C", directory, "ls-files", "-z", ...listArguments],
      { encoding: "utf8", maxBuffer: 64 * 1024 * 1024 }).split("\0").filter(Boolean);
  } catch {
    return recursiveDirFingerprint(directory);
  }
  const entries = [];
  for (const file of [...new Set(files)].sort()) {
    try { entries.push(`${file}:${hash(await readFile(resolve(directory, file)))}`); }
    catch (error) { if (error.code !== "ENOENT" && error.code !== "EISDIR") throw error; }
  }
  return hash(entries.join("\n"));
}

async function composeFingerprint(checkout) {
  let repository = null;
  try {
    repository = execFileSync("git", ["-C", checkout, "rev-parse", "--show-toplevel"], { encoding: "utf8" }).trim();
  } catch {}
  if (repository) {
    const state = gitState(checkout);
    return {
      revision: state?.revision ?? null,
      diffSha256: state?.diffSha256 ?? null,
      untrackedSha256: await contentFingerprint(repository, ["--others", "--exclude-standard"]),
    };
  }
  return {
    contentSha256: await recursiveDirFingerprint(checkout),
  };
}

async function dependenciesFingerprint(dir) {
  const resolved = resolve(dir);
  let repository = null;
  try {
    repository = execFileSync("git", ["-C", resolved, "rev-parse", "--show-toplevel"], { encoding: "utf8" }).trim();
  } catch {}
  if (repository) {
    const state = gitState(resolved);
    return {
      path: resolved,
      revision: state?.revision ?? null,
      diffSha256: state?.diffSha256 ?? null,
      untrackedSha256: await contentFingerprint(repository, ["--others", "--exclude-standard"]),
    };
  }
  return {
    path: resolved,
    contentSha256: await recursiveDirFingerprint(resolved),
  };
}

async function fingerprint() {
  const checkout = resolve(options.checkout);
  const deps = options.dependencies ? resolve(options.dependencies) : null;
  return {
    checkout,
    expectedRevision: options.revision,
    compose: await composeFingerprint(checkout),
    dependencies: deps ? await dependenciesFingerprint(deps) : null,
    sources: await contentFingerprint(rootDirectory, ["--cached", "--others", "--exclude-standard", "--", "implementations", "workloads"]),
    packageLockSha256: hash(await readFile(resolve(rootDirectory, "package-lock.json"))),
  };
}

const describe = {
  checkout: "Compose checkout path",
  expectedRevision: "expected Compose revision",
  "compose.revision": "Compose checkout HEAD",
  "compose.diffSha256": "uncommitted changes in the Compose checkout",
  "compose.untrackedSha256": "untracked files in the Compose checkout",
  "compose.contentSha256": "Compose checkout content hash",
  dependencies: "modified dependencies path",
  "dependencies.path": "modified dependencies path",
  "dependencies.revision": "modified dependencies git HEAD",
  "dependencies.diffSha256": "uncommitted changes in modified dependencies",
  "dependencies.untrackedSha256": "untracked files in modified dependencies",
  "dependencies.contentSha256": "modified dependencies content hash",
  sources: "benchmark sources (implementations, workloads)",
  packageLockSha256: "package-lock.json",
};

function differences(recorded, current, prefix = "") {
  return Object.entries(current).flatMap(([key, value]) => {
    const path = prefix + key;
    if (value && typeof value === "object") return differences(recorded?.[key] ?? {}, value, `${path}.`);
    return recorded?.[key] === value ? [] : [describe[path] ?? path];
  });
}


if (mode === "write") {
  await mkdir(resolve(rootDirectory, "build"), { recursive: true });
  await writeFile(stampPath, JSON.stringify({ verifiedAt: new Date().toISOString(), ...await fingerprint() }, null, 2) + "\n");
  console.log("Recorded verification stamp: build/verification.json");
} else if (mode === "check") {
  let recorded;
  try { recorded = JSON.parse(await readFile(stampPath, "utf8")); }
  catch { fail("This configuration has not been prepared yet."); }
  const changed = differences(recorded, await fingerprint());
  if (changed.length > 0) fail(`Preparation is stale; changed since ${recorded.verifiedAt}: ${changed.join(", ")}.`);
  for (const artifact of requiredArtifacts) {
    await access(resolve(rootDirectory, artifact))
      .catch(() => fail(`Prepared artifact is missing: ${artifact}.`));
  }
  const compId = recorded.compose?.revision?.slice(0, 12) ?? recorded.compose?.contentSha256?.slice(0, 12) ?? "custom";
  const depInfo = recorded.dependencies ? ` + deps: ${recorded.dependencies.path}` : "";
  console.log(`Using artifacts verified at ${recorded.verifiedAt} (Compose ${compId}${depInfo}).`);
} else {
  throw new Error(`Usage: verification.mjs write|check --checkout=PATH --revision=SHA [--dependencies=PATH]`);
}


function fail(message) {
  console.error(`${message}`);
  console.error("Run: ./tools/compose-version.sh --prepare");
  process.exit(1);
}
