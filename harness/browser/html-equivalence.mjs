// Compares HTML by parsed structure, attributes, namespaces, and text instead of raw strings.
// It normalizes known harmless differences between renderer outputs.
import assert from "node:assert/strict";
import { parse, parseFragment } from "parse5";

const booleanAttributes = new Set("allowfullscreen async autofocus autoplay checked controls default defer disabled formnovalidate hidden inert ismap itemscope loop multiple muted nomodule novalidate open playsinline readonly required reversed selected".split(" "));
const numericAttributes = new Set("width height x y x1 x2 y1 y2 cx cy r rx ry stroke-width stroke-dashoffset opacity fill-opacity stroke-opacity font-size offset".split(" "));

function normalizedValue(name, value) {
  if (booleanAttributes.has(name)) return "";
  if (numericAttributes.has(name) && value.trim() !== "" && Number.isFinite(Number(value))) return String(Number(value));
  if (name === "style") return value.split(";").map(v => v.trim().replace(/\s*:\s*/, ":")).filter(Boolean).sort().join(";");
  return value;
}

// Flatten iteratively: the deep-stack workload is deliberately 1,000 levels deep.
export function htmlTokens(html, fragment = false) {
  const root = fragment ? parseFragment(html) : parse(html.trim());
  const tokens = [];
  const stack = [root];
  while (stack.length) {
    const node = stack.pop();
    if (typeof node === "string") { tokens.push(node); continue; }
    if (node.nodeName === "#comment" || node.nodeName === "#documentType") continue;
    if (node.nodeName === "#text") {
      if (tokens.at(-1)?.startsWith("text:")) tokens[tokens.length - 1] += node.value;
      else tokens.push(`text:${node.value}`);
      continue;
    }
    if (node.tagName) {
      const attrs = node.attrs.map(({ name, value, prefix }) => [prefix ? `${prefix}:${name}` : name, normalizedValue(name, value)]).sort(([a], [b]) => a.localeCompare(b));
      tokens.push(`${node.namespaceURI}:${node.tagName} ${JSON.stringify(attrs)}`);
      stack.push(`/${node.tagName}`);
    }
    stack.push(...(node.childNodes ?? []).toReversed());
  }
  return tokens;
}

export function assertEquivalent(expectedHtml, actualHtml, label, fragment = false) {
  const expected = htmlTokens(expectedHtml, fragment);
  const actual = htmlTokens(actualHtml, fragment);
  const difference = expected.findIndex((token, index) => token !== actual[index]);
  assert(difference === -1 && expected.length === actual.length,
    `${label}: DOM differs at token ${difference === -1 ? expected.length : difference}\nExpected: ${expected[difference] ?? '(end)'}\nActual:   ${actual[difference] ?? '(end)'}\nToken counts: ${expected.length} / ${actual.length}`);
}
