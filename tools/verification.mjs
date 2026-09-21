// Records and checks the environment fingerprint produced by `compose-version.sh`, so that a
// measurement run can skip the build/equivalence work without silently measuring
// artifacts that no longer match their sources.
import { execFileSync } from "node:child_process";
import { access, mkdir, readFile, writeFile } from "node:fs/promises";
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

async function contentFingerprint(directory, listArguments) {
  const files = execFileSync("git", ["-C", directory, "ls-files", "-z", ...listArguments],
    { encoding: "utf8", maxBuffer: 64 * 1024 * 1024 }).split("\0").filter(Boolean);
  const entries = [];
  for (const file of [...new Set(files)].sort()) {
    try { entries.push(`${file}:${hash(await readFile(resolve(directory, file)))}`); }
    catch (error) { if (error.code !== "ENOENT" && error.code !== "EISDIR") throw error; }
  }
  return hash(entries.join("\n"));
}

// The pinned checkout is never modified by this suite, but it may be dirty; both the
// tracked diff and the untracked files change what was published to build/compose-m2.
async function composeFingerprint(checkout) {
  const repository = execFileSync("git", ["-C", checkout, "rev-parse", "--show-toplevel"], { encoding: "utf8" }).trim();
  const state = gitState(checkout);
  return {
    revision: state?.revision ?? null,
    diffSha256: state?.diffSha256 ?? null,
    untrackedSha256: await contentFingerprint(repository, ["--others", "--exclude-standard"]),
  };
}

async function fingerprint() {
  const checkout = resolve(options.checkout);
  return {
    checkout,
    expectedRevision: options.revision,
    compose: await composeFingerprint(checkout),
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
  console.log(`Using artifacts verified at ${recorded.verifiedAt} (Compose ${recorded.compose.revision?.slice(0, 12)}).`);
} else {
  throw new Error(`Usage: verification.mjs write|check --checkout=PATH --revision=SHA`);
}

function fail(message) {
  console.error(`${message}`);
  console.error("Run: ./tools/compose-version.sh --prepare");
  process.exit(1);
}
