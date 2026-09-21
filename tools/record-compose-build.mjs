import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { resolve } from "node:path";
import { gzipSync } from "node:zlib";
import { execFileSync } from "node:child_process";
import { fileHashes, gitState, hash } from "../harness/browser/run-record.mjs";
const checkout = resolve(process.argv[2]);
const dependencies = process.argv[3] ? resolve(process.argv[3]) : null;
const root = fileURLToPath(new URL("../", import.meta.url));

let repositoryRoot = null;
try {
  repositoryRoot = execFileSync("git", ["-C", checkout, "rev-parse", "--show-toplevel"], { encoding: "utf8" }).trim();
} catch {}

const untracked = {};
if (repositoryRoot) {
  try {
    for (const file of execFileSync("git", ["-C", repositoryRoot, "ls-files", "--others", "--exclude-standard", "-z"], { encoding: "utf8" }).split("\0").filter(Boolean)) {
      untracked[file] = (await readFile(resolve(repositoryRoot, file))).toString("base64");
    }
  } catch {}
}
await writeFile(resolve(root, "build/compose-untracked.json.gz"), gzipSync(JSON.stringify(untracked)));

let patch = Buffer.alloc(0);
if (repositoryRoot) {
  try {
    patch = execFileSync("git", ["-C", checkout, "diff", "HEAD", "--binary"]);
  } catch {}
}
await writeFile(resolve(root, "build/compose-source.patch"), patch);

let dependenciesProvenance = null;
if (dependencies) {
  dependenciesProvenance = {
    path: dependencies,
    git: gitState(dependencies),
  };
}

await writeFile(resolve(root, "build/compose-provenance.json"), JSON.stringify({
  checkout,
  git: gitState(checkout),
  patchSha256: hash(patch),
  dependencies: dependenciesProvenance,
  artifacts: await fileHashes(resolve(root, "build/compose-m2")),
  recordedAt: new Date().toISOString(),
}, null, 2) + "\n");
