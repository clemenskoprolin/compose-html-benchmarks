// Creates reproducible result directories with source hashes and run metadata.
import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
import { cp, mkdir, readFile, readdir, writeFile } from "node:fs/promises";
import { resolve, relative } from "node:path";
import { fileURLToPath } from "node:url";

export const rootDirectory = fileURLToPath(new URL("../../", import.meta.url));
export const hash = (bytes) => createHash("sha256").update(bytes).digest("hex");
export function gitState(directory) {
  const git = (...args) => execFileSync("git", ["-C", directory, ...args], { encoding: "utf8" }).trim();
  try {
    const status = git("status", "--porcelain");
    return { revision: git("rev-parse", "HEAD"), dirty: !!status, status, diffSha256: hash(execFileSync("git", ["-C", directory, "diff", "HEAD", "--binary"])) };
  } catch { return null; }
}
export async function fileHashes(directory) {
  const output = {};
  async function visit(dir) {
    for (const entry of await readdir(dir, { withFileTypes: true })) {
      const file = resolve(dir, entry.name);
      if (entry.isDirectory()) await visit(file);
      else if (entry.isFile() && !entry.name.endsWith(".map")) output[relative(directory, file)] = hash(await readFile(file));
    }
  }
  await visit(directory);
  return output;
}

export async function createRunRecord(kind, settings) {
  const runId = `${new Date().toISOString().replaceAll(":", "-")}-${kind}-${process.pid}`;
  const directory = resolve(rootDirectory, "results/runs", runId);
  await mkdir(directory, { recursive: true });
  const dependencies = {};
  for (const name of ["react", "react-dom", "@playwright/test", "esbuild", "parse5"]) {
    dependencies[name] = JSON.parse(await readFile(resolve(rootDirectory, "node_modules", name, "package.json"))).version;
  }
  let composeBuild = null;
  try { composeBuild = JSON.parse(await readFile(resolve(rootDirectory, "build/compose-provenance.json"))); } catch {}
  let verification = null;
  try { verification = JSON.parse(await readFile(resolve(rootDirectory, "build/verification.json"))); } catch {}
  if (composeBuild) {
    await cp(resolve(rootDirectory, "build/compose-source.patch"), resolve(directory, "compose-source.patch"));
    try { await cp(resolve(rootDirectory, "build/compose-untracked.json.gz"), resolve(directory, "compose-untracked.json.gz")); }
    catch (error) { if (error.code !== "ENOENT") throw error; }
  }
  const metadata = {
    schemaVersion: 3, runId, status: "incomplete", startedAt: new Date().toISOString(), kind, settings,
    command: [process.execPath, ...process.execArgv, ...process.argv.slice(1)],
    runtime: { node: process.version, dependencies }, repository: gitState(rootDirectory), composeBuild, verification,
    fixturesSha256: hash(await readFile(resolve(rootDirectory, "workloads/jvm/src/main/resources/fixtures.json"))),
    packageLockSha256: hash(await readFile(resolve(rootDirectory, "package-lock.json"))),
    artifacts: await fileHashes(resolve(rootDirectory, "build/web")),
    composeHtml: await fileHashes(resolve(rootDirectory, "build/ssr-html")),
    reactHtml: await fileHashes(resolve(rootDirectory, "build/react-html")),
  };
  await writeFile(resolve(directory, "metadata.json"), JSON.stringify(metadata, null, 2) + "\n");
  return {
    directory, metadata,
    async complete() {
      metadata.status = "complete";
      metadata.completedAt = new Date().toISOString();
      await writeFile(resolve(directory, "metadata.json"), JSON.stringify(metadata, null, 2) + "\n");
      const latest = resolve(rootDirectory, "results/runs/latest");
      await mkdir(latest, { recursive: true });
      for (const file of await readdir(directory)) {
        await cp(resolve(directory, file), resolve(latest, file === "metadata.json" ? `${kind}-metadata.json` : file));
      }
      console.log(`Archived run: ${directory}`);
    },
  };
}
