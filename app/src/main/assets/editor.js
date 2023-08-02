async function installScript(force = false) {
  if (!force) {
    force = confirm("Confirm ChromeXt to install this UserScript?");
  }
  if (force) {
    let script;
    if (
      document.characterSet != "UTF-8" &&
      window.location.href.startsWith("http")
    ) {
      const response = await fetch(window.location.href);
      script = await response.text();
    } else {
      script = document.body.innerText;
    }
    ChromeXt.dispatch("installScript", script);
  }
}

function editor() {
  const separator = "==/UserScript==\n";
  const code = document.querySelector("body > pre");
  const script = code.innerHTML.split(separator);

  const scriptMeta = document.createElement("pre");
  const meta = document.createElement("meta");
  const style = document.createElement("style");
  const js = document.createElement("script");
  scriptMeta.innerHTML = (script.shift() + separator).replace(
    "GM.ChromeXt",
    "<em>GM.ChromeXt</em>"
  );
  scriptMeta.id = "meta";
  code.innerHTML = script.join(separator);
  code.id = "code";
  code.removeAttribute("style");
  style.setAttribute("type", "text/css");
  meta.setAttribute("name", "viewport");
  meta.setAttribute(
    "content",
    "width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no"
  );
  style.textContent = _editor_style;
  js.setAttribute("type", "module");
  js.textContent =
    "import { highlightElement } from 'https://unpkg.com/@speed-highlight/core/dist/index.js'; highlightElement(document.querySelector('#code'), 'js', { hideLineNumbers: true });";

  document.body.prepend(scriptMeta);
  document.head.appendChild(meta);
  document.head.appendChild(style);
  document.head.appendChild(js);
  if (document.characterSet == "UTF-8") {
    scriptMeta.setAttribute("contenteditable", true);
    code.setAttribute("contenteditable", true);
  }
  setTimeout(installScript, 500);
}

if (document.readyState == "complete") {
  editor();
} else {
  window.onload = editor;
}
