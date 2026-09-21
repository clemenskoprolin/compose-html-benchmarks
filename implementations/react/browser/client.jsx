import React, { useLayoutEffect } from "react";
import { createRoot, hydrateRoot } from "react-dom/client";

function Ready({ children }) {
  useLayoutEffect(() => {
    performance.mark("app-ready");
    document.body.dataset.appReady = "true";
  }, []);
  return children;
}

export function startClient(Component, mount = false) {
  performance.mark(mount ? "react-mount-start" : "react-hydrate-start");
  const data = JSON.parse(document.querySelector("#initial-state").textContent);
  const root = document.querySelector("#app-root");
  const content = <Ready><Component data={data} /></Ready>;
  if (mount) createRoot(root).render(content);
  else hydrateRoot(root, content, {
    onRecoverableError(error) { console.error("React hydration failed:", error); },
  });
}
