async function installScript(force = false) {
  if (!force) {
    force = confirm("Confirm ChromeXt to install this UserScript?");
  }
  if (force) {
    const script = document.body.innerText;
    ChromeXt.dispatch("installScript", script);
  }
}

renderEditor = () => {
  if (window.editorReady) return;
  window.editorReady = true;

  import("https://unpkg.com/@speed-highlight/core/dist/index.js").then(
    (imports) => {
      const code = document.querySelector("body > pre");

      const separator = "==/UserScript==\n";
      const script = code.innerHTML.split(separator);
      if (separator.length == 1) return;
      const scriptMeta = document.createElement("pre");
      scriptMeta.innerHTML = (script.shift() + separator).replace(
        "GM.ChromeXt",
        "<em>GM.ChromeXt</em>"
      );
      scriptMeta.id = "meta";
      code.innerHTML = script.join(separator);
      code.id = "code";
      code.removeAttribute("style");
      document.body.prepend(scriptMeta);

      scriptMeta.setAttribute("contenteditable", true);
      code.setAttribute("contenteditable", true);
      imports.highlightElement(document.querySelector("#code"), "js", {
        hideLineNumbers: true,
      });
      setTimeout(installScript);
    }
  );
};

function prepareDOM() {
  const meta = document.createElement("meta");
  const style = document.createElement("style");

  style.setAttribute("type", "text/css");
  meta.setAttribute("name", "viewport");
  meta.setAttribute(
    "content",
    "width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"
  );
  style.textContent = _editor_style;

  document.head.appendChild(meta);
  document.head.appendChild(style);

  if (document.characterSet == "windows-1252") {
    const code = document.querySelector("body > pre");
    const text = code.textContent;
    if (!/^[\p{ASCII}]*$/u.test(text)) {
      import("https://cdn.jsdelivr.net/npm/windows-1252@3.0.4/+esm").then(
        (windows1252) => {
          const bytes_16 = windows1252.encode(code.textContent, {
            mode: "replacement",
          });
          const bytes = new Uint8Array(bytes_16);
          const utf8 = new TextDecoder();
          code.textContent = utf8.decode(bytes);
        }
      );
    }
  }
  setTimeout(renderEditor);
}

if (document.readyState == "complete") {
  prepareDOM();
} else {
  window.onload = prepareDOM;
}
