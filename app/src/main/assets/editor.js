prepareDOM();

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
  scriptMeta.setAttribute("spellcheck", false);
  code.setAttribute("spellcheck", false);
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

  await fixEncoding();
  renderEditor();
}

async function fixEncoding() {
  const gb18030 = {
    // Code taken from https://github.com/EtherDream/str2gbk/blob/main/index.js
    table: null,
    initGbkTable() {
      // https://en.wikipedia.org/wiki/GBK_(character_encoding)#Encoding
      const ranges = [
        [0xa1, 0xa9, 0xa1, 0xfe],
        [0xb0, 0xf7, 0xa1, 0xfe],
        [0x81, 0xa0, 0x40, 0xfe],
        [0xaa, 0xfe, 0x40, 0xa0],
        [0xa8, 0xa9, 0x40, 0xa0],
        [0xaa, 0xaf, 0xa1, 0xfe],
        [0xf8, 0xfe, 0xa1, 0xfe],
        [0xa1, 0xa7, 0x40, 0xa0],
      ];
      const codes = new Uint16Array(23940);
      let i = 0;
      for (const [b1Begin, b1End, b2Begin, b2End] of ranges) {
        for (let b2 = b2Begin; b2 <= b2End; b2++) {
          if (b2 !== 0x7f) {
            for (let b1 = b1Begin; b1 <= b1End; b1++) {
              codes[i++] = (b2 << 8) | b1;
            }
          }
        }
      }
      this.table = new Uint16Array(65536);
      this.table.fill(0xffff);
      const str = new TextDecoder("gbk").decode(codes);
      for (let i = 0; i < str.length; i++) {
        this.table[str.charCodeAt(i)] = codes[i];
      }
    },
    encode(str, opt = {}) {
      const defaultOnAlloc = (len) => new Uint8Array(len);
      const defaultOnError = () => 63; // '?'
      const onAlloc = opt.onAlloc || defaultOnAlloc;
      const onError = opt.onError || defaultOnError;
      const buf = onAlloc(str.length * 2);
      let n = 0;
      for (let i = 0; i < str.length; i++) {
        const code = str.charCodeAt(i);
        if (code < 0x80) {
          buf[n++] = code;
          continue;
        }
        const gbk = this.table[code];
        if (gbk !== 0xffff) {
          buf[n++] = gbk;
          buf[n++] = gbk >> 8;
        } else if (code === 8364) {
          // 8364 == '€'.charCodeAt(0)
          // Code Page 936 has a single-byte euro sign at 0x80
          buf[n++] = 0x80;
        } else {
          const ret = onError(i, str);
          if (ret === -1) {
            break;
          }
          if (ret > 0xff) {
            buf[n++] = ret;
            buf[n++] = ret >> 8;
          } else {
            buf[n++] = ret;
          }
        }
      }
      return buf.subarray(0, n);
    },
  };

  const code = document.querySelector("body > pre");
  const text = code.textContent;

  if (document.characterSet != "UTF-8" && !/^[\p{ASCII}]*$/u.test(text)) {
    let converter = () => new Uint8Array();
    if (document.characterSet == "windows-1252") {
      const windows1252 = await import(
        "https://cdn.jsdelivr.net/npm/windows-1252@latest/+esm"
      );
      converter = function (text) {
        const bytes_16 = windows1252.encode(text, {
          mode: "replacement",
        });
        return new Uint8Array(bytes_16);
      };
    } else if (document.characterSet.toLowerCase() == "gb18030") {
      gb18030.initGbkTable();
      converter = gb18030.encode.bind(gb18030);
    }
    const utf8 = new TextDecoder();
    code.textContent = code.textContent.replace(/[^\p{ASCII}]+/gu, (text) =>
      utf8.decode(converter(text))
    );
  }
}
