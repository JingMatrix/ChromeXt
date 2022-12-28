// ==UserScript==
// @name        ChromeXt cosmetic filters
// @namespace   JingMatrix
// @match       https://*
// @match       http://*
// @run-at      document-start
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
