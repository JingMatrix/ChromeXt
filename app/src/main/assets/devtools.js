window.addEventListener("load", () => {
  const meta = document.createElement("meta");
  meta.setAttribute("name", "viewport");
  meta.setAttribute("content", "width=device-width, initial-scale=1");
  document.head.prepend(meta);
  const style = document.createElement("style");
  style.innerText = ".filter-bitset-filter { overflow-x: scroll !important; }";
  document.body.prepend(style);
});
