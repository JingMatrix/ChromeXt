// ==UserScript==
// @name        ChromeXt cosmetic filters
// @namespace   JingMatrix
// @match       https://*
// @match       http://*
// @run-at      document-end
// @grant       GM_addStyle
// ==/UserScript==

let filter = localStorage.getItem("ChromeXt_filter");
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

const removeIframe = () => {
  iframes = document.querySelectorAll("iframe");
  iframes.forEach((node) => {
    node.contentWindow.window.length == 0 && node.remove();
  });
};
removeIframe();

let iframeCleaner = new MutationObserver(removeIframe);
iframeCleaner.observe(document.body, {
  attributes: false,
  characterData: false,
  childList: true,
  subtree: true,
});
setTimeout(() => iframeCleaner.disconnect(), 1000 * 10);
