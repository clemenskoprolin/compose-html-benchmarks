import { renderToPipeableStream } from "react-dom/server";
import { bodyElement, documentElement } from "./renderer.jsx";

export function streamBodyComponent(Component, data, options) {
  return renderToPipeableStream(bodyElement(Component, data), options);
}

export function streamDocumentComponent(Component, data, options, catalog = false, includeDescription = false) {
  return renderToPipeableStream(documentElement(Component, data, catalog, includeDescription), options);
}
