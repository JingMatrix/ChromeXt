async function installScript(force = false) {
  const dialog = document.querySelector("dialog#confirm");
  if (!force) {
    dialog.showModal();
  } else {
    dialog.close();
    const script = document.body.innerText;
    ChromeXt.dispatch("installScript", script);
  }
}

function renderEditor() {
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
  createDialog();
  setTimeout(fixDialog);
  import("https://unpkg.com/@speed-highlight/core/dist/index.js").then(
    (imports) => {
      imports.highlightElement(document.querySelector("#code"), "js", {
        hideLineNumbers: true,
      });
    }
  );
}

function createDialog() {
  const dialog = document.createElement("dialog");
  dialog.id = "confirm";
  dialog.textContent =
    "Code editor is blocked on this page.\n\nPlease use page menu to install this UserScript, or reload current page to enable the editor.";
  document.body.prepend(dialog);
  dialog.show();
}

function fixDialog() {
  const dialog = document.querySelector("dialog#confirm");
  if (dialog.textContent == "") return;
  dialog.close();
  dialog.textContent = "";
  const text = document.createElement("p");
  text.textContent = "Confirm ChromeXt to install this UserScript?";
  const div = document.createElement("div");
  div.id = "interaction";
  const yes = document.createElement("button");
  yes.textContent = "Confirm";
  yes.addEventListener("click", () => installScript(true));
  const no = document.createElement("button");
  no.addEventListener("click", () => {
    dialog.close();
    setTimeout(() => dialog.show(), 30000);
  });
  no.textContent = "Ask 30s later";
  div.append(yes);
  div.append(no);
  dialog.append(text);
  const askChromeXt = document.querySelector("#meta > em") != undefined;
  if (askChromeXt) {
    const alert = document.createElement("p");
    alert.id = "alert";
    alert.textContent = "ATTENTION: GM.ChromeXt is declared";
    dialog.append(alert);
  }
  dialog.append(div);
  installScript();
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

  if (!document.head) {
    window.addEventListener("DOMContentLoaded", prepareDOM);
    return;
  }
  document.head.appendChild(meta);
  document.head.appendChild(style);

  if (document.characterSet == "windows-1252") {
    const code = document.querySelector("body > pre");
    const text = code.textContent;
    if (!/^[\p{ASCII}]*$/u.test(text)) {
      const windows1252 = await import(
        "https://cdn.jsdelivr.net/npm/windows-1252@latest/+esm"
      );
      code.textContent = code.textContent.replace(/[^\p{ASCII}]+/gu, (text) => {
        const bytes_16 = windows1252.encode(text, {
          mode: "replacement",
        });
        const bytes = new Uint8Array(bytes_16);
        const utf8 = new TextDecoder();
        return utf8.decode(bytes);
      });
    }
  }
  renderEditor();
}

prepareDOM();
