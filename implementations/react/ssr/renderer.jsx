import React from "react";
import { renderToString } from "react-dom/server";

export function renderBodyComponent(Component, data) {
  return renderToString(bodyElement(Component, data));
}

export function renderDocumentComponent(Component, data, catalog = false, includeDescription = false) {
  return renderToString(documentElement(Component, data, catalog, includeDescription));
}

export function bodyElement(Component, data) {
  return <Component data={data} />;
}

export function documentElement(Component, data, catalog = false, includeDescription = false) {
  return <html lang="en"><head>
    <meta charSet="UTF-8" />
    <title>{data.title}</title>
    {catalog && <link rel="stylesheet" href="/styles.css" />}
    {includeDescription && <meta name="description" content={data.excerpt} />}
  </head><body className={catalog ? "bg-slate-50 text-slate-900 font-sans antialiased min-h-screen" : undefined}>
    <Component data={data} />
  </body></html>;
}
