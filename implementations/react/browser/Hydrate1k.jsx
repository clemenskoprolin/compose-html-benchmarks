import React, { useCallback, useState } from "react";

// Adapted from preactjs/benchmarks apps/table-app (MIT, commit ec93e1b).
function Row({ data, selected, onSelect, onDelete }) {
  return (
    <tr className={selected ? "danger" : undefined}>
      <td className="col-md-1">{data.id}</td>
      <td className="col-md-4">
        <a onClick={() => onSelect(data.id)}>
          {data.label}
        </a>
      </td>
      <td className="col-md-1">
        <a onClick={() => onDelete(data.id)}>
          <span className="glyphicon glyphicon-remove" aria-hidden="true" />
        </a>
      </td>
      <td className="col-md-6" />
    </tr>
  );
}

export function Hydrate1k({ data }) {
  const [rows, setRows] = useState(data.rows);
  const [selectedRowId, setSelectedRowId] = useState(null);
  const deleteRow = useCallback((id) => {
    setRows((currentRows) => currentRows.filter((row) => row.id !== id));
  }, []);


  return (
    <div className="container">
      <table className="table table-hover table-striped test-data">
        <tbody>
          {rows.map((row) => (
            <Row
              key={row.id}
              data={row}
              selected={row.id === selectedRowId}
              onSelect={setSelectedRowId}
              onDelete={deleteRow}
            />
          ))}
        </tbody>
      </table>
      <span
        className="preloadicon glyphicon glyphicon-remove"
        aria-hidden="true"
      />
    </div>
  );
}
