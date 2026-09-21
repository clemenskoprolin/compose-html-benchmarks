import React, { useCallback, useState } from "react";

function Row({ data, selected, onSelect }) {
  return <tr className={selected ? "danger" : undefined}>
    <td className="col-md-1">{data.id}</td>
    <td className="col-md-4"><a onClick={() => onSelect(data.id)}>{data.label}</a></td>
    <td className="col-md-1"><a><span className="glyphicon glyphicon-remove" aria-hidden="true" /></a></td>
    <td className="col-md-6" />
  </tr>;
}

// Adapted from preactjs/benchmarks apps/table-app/update10th1k.html (MIT, commit ec93e1b).
export function Update10th1k({ data }) {
  const [rows, setRows] = useState(data.rows);
  const [selectedRowId, setSelectedRowId] = useState(null);
  const update = useCallback(() => setRows(current => current.map((row, index) =>
    index % 10 === 0 ? { ...row, label: `${row.label} !!!` } : row
  )), []);
  return <div className="container"><table className="table table-hover table-striped test-data"><tbody>
    {rows.map(row => <Row key={row.id} data={row} selected={row.id === selectedRowId} onSelect={setSelectedRowId} />)}
  </tbody></table><span id="benchmark-update" className="preloadicon glyphicon glyphicon-remove" aria-hidden="true" onClick={update} /></div>;
}
