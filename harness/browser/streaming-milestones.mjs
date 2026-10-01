// Locate milestones outside the timed region; the sink only counts delivered UTF-8 bytes.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { parse } from "parse5";

export const first8KiBBytes = 8192;
export const streamingMilestones = JSON.parse(readFileSync(
  new URL("../streaming-milestones.json", import.meta.url), "utf8"));

function matches(node, rule) {
  if (node.tagName !== rule.tag) return false;
  const attributes = Object.fromEntries((node.attrs ?? []).map(attribute => [attribute.name, attribute.value]));
  const classes = new Set((attributes.class ?? "").split(/\s+/));
  if (rule.classes?.some(name => !classes.has(name))) return false;
  if (Object.entries(rule.attributes ?? {}).some(([name, value]) => attributes[name] !== value)) return false;
  if (rule.ancestor) {
    let parent = node.parentNode;
    while (parent && parent.tagName !== rule.ancestor) parent = parent.parentNode;
    if (!parent) return false;
  }
  return true;
}

export function contentByteOffset(html, scenario) {
  const milestone = streamingMilestones[scenario];
  assert(milestone, `Unknown streaming scenario: ${scenario}`);
  const document = parse(html, { sourceCodeLocationInfo: true });
  const ends = milestone.elements.map(rule => {
    const pending = [document];
    while (pending.length) {
      const node = pending.pop();
      if (matches(node, rule)) {
        const location = node.sourceCodeLocation;
        // Void inputs finish at their opening tag; other milestones require an explicit closing tag.
        const end = rule.tag === "input" ? location?.startTag?.endOffset : location?.endTag?.endOffset;
        assert(Number.isInteger(end), `${scenario}: milestone <${rule.tag}> has no complete source range`);
        return { node, end };
      }
      pending.push(...(node.childNodes ?? []).toReversed());
    }
    assert.fail(`${scenario}: missing milestone <${rule.tag}>`);
  });
  if (scenario === "form-app") {
    const attribute = (node, name) => node.attrs.find(attribute => attribute.name === name)?.value;
    assert(attribute(ends[1].node, "id"), "First input must have an id");
    assert.equal(attribute(ends[0].node, "for"), attribute(ends[1].node, "id"), "First input must match its label");
  }
  return Buffer.byteLength(html.slice(0, Math.max(...ends.map(item => item.end))), "utf8");
}

export class StreamingCounter {
  constructor(contentBytes = null, captureWrites = false) {
    this.contentBytes = contentBytes;
    this.writes = captureWrites ? [] : null;
    this.firstChunkMs = null;
    this.firstContentMs = null;
    this.first8KiBMs = null;
    this.bytes = 0;
    this.chunks = 0;
  }

  accept(chunk, elapsedMs) {
    if (!chunk.length) return;
    this.firstChunkMs ??= elapsedMs;
    this.chunks++;
    this.bytes += chunk.length;
    if (this.bytes >= first8KiBBytes) this.first8KiBMs ??= elapsedMs;
    if (this.contentBytes !== null && this.bytes >= this.contentBytes) this.firstContentMs ??= elapsedMs;
    this.writes?.push({ bytes: this.bytes, elapsedMs });
  }

  resolveContent(contentBytes) {
    this.firstContentMs = this.writes?.find(write => write.bytes >= contentBytes)?.elapsedMs ?? null;
    assert(this.firstContentMs !== null, "Content milestone never reached the sink");
  }
}
