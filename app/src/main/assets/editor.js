async function installScript(force = false) {
  if (!force) {
    force = confirm("Confirm ChromeXt to install this UserScript?");
  }
  if (force) {
    const script = document.body.innerText;
    ChromeXt.dispatch("installScript", script);
  }
}

function renderEditor() {
  if (window.editorReady) return;
  window.editorReady = true;

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
  setTimeout(installScript);
  import("https://unpkg.com/@speed-highlight/core/dist/index.js").then(
    (imports) => {
      imports.highlightElement(document.querySelector("#code"), "js", {
        hideLineNumbers: true,
      });
    }
  );
}

async function prepareDOM() {
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
      const windows1252 = await import(
        "https://cdn.jsdelivr.net/npm/windows-1252@latest/+esm"
      );
      const bytes_16 = windows1252.encode(code.textContent, {
        mode: "replacement",
      });
      const bytes = new Uint8Array(bytes_16);
      const utf8 = new TextDecoder();
      code.textContent = utf8.decode(bytes);
    }
  }
  renderEditor();
}

if (document.readyState == "complete") {
  prepareDOM();
} else {
  window.onload = prepareDOM;
}
