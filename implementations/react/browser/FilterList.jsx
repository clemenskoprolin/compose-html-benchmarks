import React, { useCallback, useState } from "react";

const COUNT = 1000;
const RANGE_START = 20;
const RANGE_END = 600;

function initialItems() {
  return Array.from({ length: COUNT }, (_, i) => i);
}

function nextItems(current) {
  const currentSet = new Set(current);
  return initialItems().filter((id) => {
    const isVisible = currentSet.has(id);
    return id >= RANGE_START && id <= RANGE_END ? !isVisible : isVisible;
  });
}

// Adapted from preactjs/benchmarks apps/filter-list (MIT, commit ec93e1b).
export function FilterList({ data }) {
  const [items, setItems] = useState(() => data.items ?? initialItems());

  const runPatch = useCallback(() => {
    setItems((current) => nextItems(current));
  }, []);


  return (
    <div>
      <div className="items">
        {items.map((id) => (
          <article key={id}>{id}</article>
        ))}
      </div>
      {/* Hidden trigger span for the benchmark harness (analogous to #benchmark-update / #benchmark-reorder). */}
      <span
        id="benchmark-filter"
        className="preloadicon"
        aria-hidden="true"
        onClick={runPatch}
      />
    </div>
  );
}
