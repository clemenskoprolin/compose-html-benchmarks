import React from "react";

export function SvgDashboard({ data }) {

  return (
    <div className="dashboard-container">
      <header className="dashboard-header">
        <h1>{data.title}</h1>
      </header>

      <div className="charts-grid">
        {/* Line Chart */}
        <div
          className="chart-wrapper"
          aria-label={`Line chart showing ${data.lineChart.title}`}
          role="img"
        >
          <h2>{data.lineChart.title}</h2>
          <svg viewBox="0 0 800 400" width="100%" height="100%">
            <line x1="50" y1="350" x2="750" y2="350" stroke="#000" strokeWidth="2" />
            <line x1="50" y1="50" x2="50" y2="350" stroke="#000" strokeWidth="2" />
            <path
              d={data.lineChart.pathData}
              fill="none"
              stroke="#3b82f6"
              strokeWidth="3"
            />
            {data.lineChart.points.map((point, idx) => (
              <circle key={idx} cx={point.x} cy={point.y} r="4" fill="#1d4ed8" />
            ))}
            {(data.lineChart.xLabels || data.lineChart.xlabels || []).map((label, idx) => (
              <text key={idx} x={label.x} y="370" textAnchor="middle" fontSize="12">
                {label.text}
              </text>
            ))}
          </svg>
        </div>

        {/* Bar Chart */}
        <div
          className="chart-wrapper"
          aria-label={`Bar chart showing ${data.barChart.title}`}
          role="img"
        >
          <h2>{data.barChart.title}</h2>
          <svg viewBox="0 0 600 400">
            <defs>
              <linearGradient id="bar-grad" x1="0%" y1="100%" x2="0%" y2="0%">
                <stop offset="0%" stopColor="#34d399" />
                <stop offset="100%" stopColor="#059669" />
              </linearGradient>
            </defs>
            {data.barChart.bars.map((bar, idx) => (
              <React.Fragment key={idx}>
                <rect
                  x={bar.x}
                  y={bar.y}
                  width={bar.width}
                  height={bar.height}
                  fill="url(#bar-grad)"
                />
                <text
                  x={bar.x + bar.width / 2}
                  y={bar.y - 10}
                  textAnchor="middle"
                  fontSize="12"
                >
                  {bar.value}
                </text>
              </React.Fragment>
            ))}
          </svg>
        </div>

        {/* Pie Chart */}
        <div
          className="chart-wrapper"
          aria-label={`Pie chart showing ${data.pieChart.title}`}
          role="img"
        >
          <h2>{data.pieChart.title}</h2>
          <svg viewBox="0 0 400 400">
            <g transform="translate(200, 200)">
              {data.pieChart.slices.map((slice, idx) => (
                <path
                  key={idx}
                  d={slice.pathData}
                  fill={slice.color}
                  stroke="#fff"
                  strokeWidth="2"
                />
              ))}
            </g>
          </svg>
        </div>

        {/* Gauge Meter */}
        <div
          className="chart-wrapper"
          aria-label={`Gauge showing ${data.gauge.title}`}
          role="img"
        >
          <h2>{data.gauge.title}</h2>
          <svg viewBox="0 0 200 150">
            <path
              d="M 20 130 A 80 80 0 0 1 180 130"
              fill="none"
              stroke="#e5e7eb"
              strokeWidth="20"
            />
            <path
              d={data.gauge.valuePathData}
              fill="none"
              stroke="#ef4444"
              strokeWidth="20"
            />
            <line
              x1="100"
              y1="130"
              x2={data.gauge.needleX}
              y2={data.gauge.needleY}
              stroke="#374151"
              strokeWidth="4"
            />
            <circle cx="100" cy="130" r="8" fill="#374151" />
            <text
              x="100"
              y="145"
              textAnchor="middle"
              fontSize="14"
              dominantBaseline="hanging"
            >
              {data.gauge.valueLabel}
            </text>
          </svg>
        </div>
      </div>
    </div>
  );
}
