// ==UserScript==
// @name        ChromeXt cosmetic filters
// @namespace   JingMatrix
// @match       https://*
// @match       http://*
// @run-at      document-end
// ==/UserScript==

let filter = localStorage.getItem("ChromeXt_filter");
if (filter != null) {
  filter = JSON.parse(filter);
  window.addEventListener("load", () => {
    document.querySelectorAll(filter.join(", ")).forEach((node) => {
      node.hidden = true;
      node.style.display = "none";
      node.style.visibility = "hidden";
    });
  });
}

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
