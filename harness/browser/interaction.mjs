// Browser-page helper that times an interaction until its expected DOM update has committed.
// Wait for the expected DOM change before the frame boundary:
// two rAF callbacks alone can finish before an asynchronously scheduled commit.
export async function interactionInPage({ selector, scenario, operationCount }) {
  const trigger = document.querySelector(selector);
  if (!trigger) throw new Error(`Missing interaction trigger: ${selector}`);
  const complete = () => {
    if (scenario === "update10th1k") {
      return document.querySelector(".test-data tbody > tr:nth-child(991) td:nth-child(2)")
        ?.textContent?.endsWith(" !!!".repeat(operationCount));
    }
    if (scenario === "reorder1k") {
      return document.querySelector(".test-data tbody > tr:first-child td:first-child")
        ?.textContent === String((3 * operationCount) % 1000 + 1);
    }
    return document.querySelectorAll(".items > article").length === (operationCount % 2 ? 419 : 1000);
  };
  const start = performance.now();
  await new Promise((resolve, reject) => {
    const observer = new MutationObserver(check);
    const timeout = setTimeout(() => {
      observer.disconnect();
      reject(new Error(`Interaction did not complete: ${scenario} #${operationCount}`));
    }, 30_000);
    function check() {
      if (!complete()) return;
      observer.disconnect();
      clearTimeout(timeout);
      resolve();
    }
    observer.observe(document.body, { subtree: true, childList: true, characterData: true });
    trigger.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    check();
  });
  await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)));
  return performance.now() - start;
}
