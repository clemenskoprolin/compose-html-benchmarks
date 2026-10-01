// Column definitions and formatted cells are shared by terminal and Markdown reports.
export function formatCell(column, value) {
  if (value == null) return "n/a";
  if (typeof value !== "number") return String(value);
  const number = column.decimals == null ? String(value) : value.toFixed(column.decimals);
  return number + (column.suffix ?? "");
}

export function tableRow(columns, cells) {
  return cells.map((cell, index) => {
    const text = formatCell(columns[index], cell);
    return columns[index].align === "right"
      ? text.padStart(columns[index].width) : text.padEnd(columns[index].width);
  }).join(" ");
}

export function consoleTable({ title, columns, groups, width, blankRepeatedFirstCell = true }) {
  const rule = "-".repeat(width);
  const lines = ["=".repeat(width), title, "=".repeat(width),
    tableRow(columns, columns.map(column => column.label)), rule];
  for (const group of groups) {
    group.forEach((cells, index) => lines.push(tableRow(columns,
      index && blankRepeatedFirstCell ? ["", ...cells.slice(1)] : cells)));
    lines.push(rule);
  }
  return lines.join("\n");
}

export function markdownTable(columns, rows) {
  const escape = value => String(value).replaceAll("|", "\\|").replaceAll("\n", "<br>");
  const row = cells => `| ${cells.map((value, index) => escape(formatCell(columns[index], value))).join(" | ")} |`;
  return [row(columns.map(column => column.label)),
    row(columns.map(column => column.align === "right" ? "---:" : ":---")),
    ...rows.map(row)].join("\n");
}
