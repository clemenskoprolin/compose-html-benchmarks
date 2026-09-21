import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { resolve } from "node:path";
import { gzipSync } from "node:zlib";
import { execFileSync } from "node:child_process";
import { fileHashes, gitState, hash } from "../harness/browser/run-record.mjs";
const checkout = resolve(process.argv[2]);
const root = fileURLToPath(new URL("../", import.meta.url));
const repositoryRoot = execFileSync("git", ["-C", checkout, "rev-parse", "--show-toplevel"], { encoding: "utf8" }).trim();
const untracked = {};
for (const file of execFileSync("git", ["-C", repositoryRoot, "ls-files", "--others", "--exclude-standard", "-z"], { encoding: "utf8" }).split("\0").filter(Boolean)) {
  untracked[file] = (await readFile(resolve(repositoryRoot, file))).toString("base64");
}
await writeFile(resolve(root, "build/compose-untracked.json.gz"), gzipSync(JSON.stringify(untracked)));
const patch = execFileSync("git", ["-C", checkout, "diff", "HEAD", "--binary"]);
await writeFile(resolve(root, "build/compose-source.patch"), patch);
await writeFile(resolve(root, "build/compose-provenance.json"), JSON.stringify({
  checkout, git: gitState(checkout), patchSha256: hash(patch),
  artifacts: await fileHashes(resolve(root, "build/compose-m2")), recordedAt: new Date().toISOString(),
}, null, 2) + "\n");
