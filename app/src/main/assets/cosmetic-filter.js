window.addEventListener("DOMContentLoaded", () => {
  function GM_addStyle(css) {
    const style = document.createElement("style");
    style.setAttribute("type", "text/css");
    style.textContent = css;
    document.head.appendChild(style);
  }

  let filter = ChromeXt.filters;
  if (filter != null) {
    filter = JSON.parse(filter).join(", ");
    try {
      GM_addStyle(filter + " {display: none !important;}");
    } finally {
      window.addEventListener("load", () => {
        document.querySelectorAll(filter).forEach((node) => {
          node.hidden = true;
          node.style.display = "none";
        });
      });
    }
  }

  document
    .querySelectorAll("amp-ad,amp-embed,amp-sticky-ad")
    .forEach((node) => node.remove());
});
