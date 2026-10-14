import React from "react";
import { BAVARIA_FOO, BAVARIA_BAR } from "../../../workloads/text-prose.mjs";

// Adapted from preact-render-to-string benchmarks.

function Bavaria() {
  return (
    <div>
      <span className="foo" data-testid="foo">{BAVARIA_FOO}</span>
      <span className="bar" data-testid="bar">{BAVARIA_BAR}</span>
    </div>
  );
}

export function PreactText() {
  return (
    <div>
      {Array.from({ length: 1_000 }, (_, index) => (
        <Bavaria key={index} />
      ))}
    </div>
  );
}
