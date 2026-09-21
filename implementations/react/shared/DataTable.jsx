import React from "react";

export function DataTable({ data }) {

  return (
    <div id="dashboard" data-page="data-table">
      {/* Header */}
      <header className="page-header">
        <h1>{data.title}</h1>
        <p>{data.description}</p>
      </header>

      {/* Filters Bar */}
      <div className="filters-bar" role="search">
        <input
          type="search"
          id="table-search"
          placeholder="Search rows..."
          aria-label="Search rows"
          defaultValue={data.searchQuery || undefined}
        />
        <div className="active-filters">
          {Object.entries(data.activeFilters).map(([key, val]) => (
            <span
              key={key}
              className="filter-badge"
              data-filter-key={key}
              data-filter-val={val}
            >
              {key}: {val}
            </span>
          ))}
        </div>
      </div>

      {/* Selection Status Bar */}
      <div className="selection-status" role="status" aria-live="polite">
        {data.selectedRowCount} items selected
      </div>

      {/* Table */}
      <table
        role="grid"
        aria-rowcount={data.totalRowCount}
        aria-colcount={data.headers.length}
        aria-label="Data Table"
      >
        <thead>
          <tr>
            <th scope="col">
              <input type="checkbox" aria-label="Select all rows" />
            </th>
            {data.headers.map((header) => {
              const sortDir =
                header.id === data.sortColumnId ? data.sortDirection : "none";
              const classNames = ["sortable"];
              if (sortDir !== "none") classNames.push(`sorted-${sortDir}`);

              return (
                <th
                  key={header.id}
                  scope="col"
                  data-column-id={header.id}
                  role="columnheader"
                  className={header.sortable ? classNames.join(" ") : undefined}
                  aria-sort={header.sortable ? sortDir : undefined}
                >
                  {header.label}
                </th>
              );
            })}
          </tr>
        </thead>
        <tbody>
          {data.rows.map((row) => (
            <tr
              key={row.id}
              data-row-id={row.id}
              data-status={row.status}
              aria-selected={row.selected ? "true" : "false"}
              className={row.selected ? "row-selected" : undefined}
            >
              <td>
                <input
                  type="checkbox"
                  aria-label={`Select row ${row.id}`}
                  defaultChecked={row.selected}
                />
              </td>
              {row.cells.map((cell, cIdx) => (
                <td
                  key={cIdx}
                  data-column={cell.columnId}
                  data-value={cell.rawValue}
                  aria-describedby={
                    cell.tooltip ? `tt-${row.id}-${cell.columnId}` : undefined
                  }
                >
                  {cell.isStatus ? (
                    <span
                      className={`status-badge status-${cell.rawValue}`}
                      role="status"
                    >
                      {cell.displayValue}
                    </span>
                  ) : (
                    cell.displayValue
                  )}
                  {cell.tooltip && (
                    <span
                      id={`tt-${row.id}-${cell.columnId}`}
                      className="tooltip-text"
                      style={{ display: "none" }}
                    >
                      {cell.tooltip}
                    </span>
                  )}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>

      {/* Pagination */}
      <nav aria-label="Pagination">
        <ul className="pagination">
          {data.pages.map((page) => (
            <li key={page.number}>
              <a
                href={`?page=${page.number}`}
                data-page={page.number}
                aria-label={`Page ${page.number}`}
                aria-current={page.isCurrent ? "page" : undefined}
                className={page.isCurrent ? "current-page" : undefined}
              >
                {page.number}
              </a>
            </li>
          ))}
        </ul>
      </nav>
    </div>
  );
}
