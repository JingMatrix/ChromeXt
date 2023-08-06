async function installScript(force = false) {
  if (!force) {
    force = confirm("Confirm ChromeXt to install this UserScript?");
  }
  if (force) {
    const script = document.body.innerText;
    ChromeXt.dispatch("installScript", script);
  }
}

renderEditor = (windows1252, highlightElement) => {
  const code = document.querySelector("body > pre");

  if (document.characterSet == "windows-1252") {
    const bytes_16 = windows1252.encode(code.textContent, {
      mode: "replacement",
    });
    const bytes = new Uint8Array(bytes_16);
    const utf8 = new TextDecoder();
    code.textContent = utf8.decode(bytes);
  }

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

  highlightElement(document.querySelector("#code"), "js", {
    hideLineNumbers: true,
  });

  scriptMeta.setAttribute("contenteditable", true);
  code.setAttribute("contenteditable", true);
  setTimeout(installScript);
};

function prepareDOM() {
  const meta = document.createElement("meta");
  const style = document.createElement("style");
  const js = document.createElement("script");

  style.setAttribute("type", "text/css");
  meta.setAttribute("name", "viewport");
  meta.setAttribute(
    "content",
    "width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"
  );
  style.textContent = _editor_style;
  js.setAttribute("type", "module");
  js.textContent =
    "import * as windows1252 from 'https://cdn.jsdelivr.net/npm/windows-1252@3.0.4/+esm'; import { highlightElement } from 'https://unpkg.com/@speed-highlight/core/dist/index.js'; renderEditor(windows1252, highlightElement);";

  document.head.appendChild(meta);
  document.head.appendChild(style);
  document.head.appendChild(js);
}

if (document.readyState == "complete") {
  prepareDOM();
} else {
  window.onload = prepareDOM;
}
