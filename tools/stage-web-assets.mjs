import { cp, mkdir, rm } from "node:fs/promises";

const repositoryRoot = new URL("../", import.meta.url);
const source = new URL("vendor/compose-catalog/static/", repositoryRoot);
const destination = new URL("build/web/static/", repositoryRoot);

await rm(destination, { force: true, recursive: true });
await mkdir(destination, { recursive: true });
await cp(source, destination, { recursive: true });

console.log("Staged pinned Compose catalog artifacts in build/web/static.");
