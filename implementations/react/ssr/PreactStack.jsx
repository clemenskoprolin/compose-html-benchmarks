import React from "react";

function StackLeaf() {
  return <div><span className="foo" data-testid="stack">deep stack</span></div>;
}

function PassThrough({ children }) {
  return <div>{children}</div>;
}

function stackBranch(depth) {
  return depth === 0 ? <StackLeaf /> : <PassThrough>{stackBranch(depth - 1)}</PassThrough>;
}

export function PreactStack() {
  return (
    <div>
      {Array.from({ length: 10 }, (_, index) =>
        React.cloneElement(stackBranch(1_000), { key: index })
      )}
    </div>
  );
}
