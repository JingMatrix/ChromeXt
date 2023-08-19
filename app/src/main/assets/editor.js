const invalidChar = "�";

ChromeXt.addEventListener("userscript", (e) => {
  const dialog = document.querySelector("dialog#confirm");
  let code = document.querySelector("#code");
  const scriptMeta = document.querySelector("#meta");
  if (document.body.innerText.includes(invalidChar)) {
    if (!code) code = document.querySelector("body > pre");
    const file = e.detail.code;
    code.textContent = file;
    if (!dialog) createDialog("Encoding fixed by ChromeXt");
    setTimeout(fixDialog);
    if (scriptMeta) scriptMeta.remove();
    renderEditor(code, false);
  }
});

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

function renderEditor(code, checkEncoding = true) {
  let scriptMeta = document.querySelector("#meta");
  if (scriptMeta) return;
  const separator = "==/UserScript==\n";
  const script = code.innerHTML.split(separator);
  if (separator.length == 1) return;
  scriptMeta = document.createElement("pre");
  scriptMeta.innerHTML = (script.shift() + separator).replace(
    "GM.ChromeXt",
    "<em>GM.ChromeXt</em>"
  );
  code.innerHTML = script.join(separator);
  code.id = "code";
  code.removeAttribute("style");
  scriptMeta.id = "meta";
  document.body.prepend(scriptMeta);

  if (code.textContent.includes(invalidChar) && checkEncoding) {
    const msg =
      "Current script may contain badly decoded text.\n\nTo fix possible issues, you can change the browser UI language to English.";
    createDialog(msg, false);
  } else {
    if (checkEncoding) {
      const msg =
        "Code editor is blocked on this page.\n\nPlease use page menu to install this UserScript, or reload current page to enable the editor.";
      createDialog(msg);
      setTimeout(fixDialog);
    }

    scriptMeta.setAttribute("contenteditable", true);
    code.setAttribute("contenteditable", true);
    scriptMeta.setAttribute("spellcheck", false);
    code.setAttribute("spellcheck", false);
    import("https://unpkg.com/@speed-highlight/core/dist/index.js").then(
      (imports) => {
        imports.highlightElement(document.querySelector("#code"), "js", {
          hideLineNumbers: true,
        });
      }
    );
  }
}

function createDialog(msg) {
  const dialog = document.createElement("dialog");
  dialog.id = "confirm";
  dialog.textContent = msg;
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

  const code = document.querySelector("body > pre");
  const text = code.textContent;
  if (document.characterSet != "UTF-8" && !/^[\p{ASCII}]*$/u.test(text)) {
    fixEncoding(code);
    if (code.textContent.includes(invalidChar)) {
      try {
        const text = await fetch("").then((res) => res.text());
        if (text.includes(invalidChar)) throw new Error("Non UTF-8 text");
        code.textContent = text;
      } catch {
        console.error("Failed to fetch a UTF-8 encoded document");
      }
    }
  }
  renderEditor(code);
}

class Encoding {
  #name;
  decoder = new TextDecoder();
  get encoding() {
    return this.#name;
  }
  map = new Map();
  constructor(name = "utf-8") {
    this.#name = name.toLowerCase();
  }
  defaultOnError(_input, index, result) {
    result[index] = 0xff;
  }
  defaultOnAlloc = (len) => new Uint8Array(len);
  generateTable() {
    return new Map();
  }
  encode(input, opt = {}) {
    if (!(this.table instanceof Map))
      Object.defineProperty(this, "table", {
        value: new Map([...this.generateTable(), ...this.map]),
      });
    if (this.encoding == "utf-8") return new TextEncoder().encode(input);
    const onError = opt.onError || this.defaultOnError.bind(this);
    const onAlloc = opt.onAlloc || this.defaultOnAlloc.bind(this);
    const length = input.length;
    const result = onAlloc(length);
    for (let i = 0; i < length; i++) {
      let charCode = input.charCodeAt(i);
      if (charCode <= 0xdbff && charCode >= 0xd800) {
        // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String
        i++;
        charCode += input.charCodeAt(i);
      }
      if (0x00 <= charCode && charCode < 0x80) {
        result[i] = charCode;
      } else if (this.table.has(charCode)) {
        result[i] = this.table.get(charCode);
      } else if (input[i] == invalidChar) {
        const ret = onError(input, i, result);
        if (ret === -1) {
          break;
        }
        // } else {
        // console.error("CharCode for", charCode > 0xffff ? input[i - 1] + input[i] : input[i], "not found in encoding", this.encoding);
      }
    }
    return new Uint8Array(result.buffer).filter((c) => c != 0x00);
  }
  decode(uint8) {
    return new TextDecoder(this.#name).decode(uint8);
  }
  convert(text) {
    return this.decoder.decode(this.encode(text));
  }
}

class SingleByte extends Encoding {
  generateTable() {
    const range = [...Array(0x80).keys()];
    const codePoints = new Uint8Array(range.map((x) => x + 0x80));
    const str = this.decode(codePoints);
    return new Map(range.map((i) => [str.charCodeAt(i), codePoints[i]]));
  }
}

class TwoBytes extends Encoding {
  intervals = [[0x81, 0xfe, 0x40, 0xfe]];
  generateTable() {
    const map = [];
    this.intervals.forEach(([b1Begin, b1End, b2Begin, b2End]) => {
      for (let b1 = b1Begin; b1 <= b1End; b1++) {
        for (let b2 = b2Begin; b2 <= b2End; b2++) {
          const code = (b2 << 8) | b1;
          const str = this.decode(new Uint16Array([code]));
          if (str.includes(invalidChar)) continue;
          let charCode = str.charCodeAt(0);
          if (charCode <= 0xdbff && charCode >= 0xd800) {
            map.push([charCode + str.charCodeAt(1), code]);
          } else {
            map.push([charCode, code]);
          }
        }
      }
    });
    return map;
  }
  defaultOnAlloc = (len) => new Uint16Array(len);
}

class GBK extends TwoBytes {
  // https://en.wikipedia.org/wiki/GBK_(character_encoding)
  map = new Map([["€".charCodeAt(0), 0x80]]);
}

function fixEncoding(code) {
  let converter = () => invalidChar;
  const encoding = document.characterSet.toLowerCase();
  if (
    encoding.startsWith("windows") ||
    encoding.startsWith("iso-8859") ||
    encoding.startsWith("koi") ||
    encoding.startsWith("ibm") ||
    encoding.includes("mac")
  ) {
    const encoder = new SingleByte(encoding);
    converter = encoder.convert.bind(encoder);
  } else if (encoding.startsWith("gb")) {
    const encoder = new GBK(encoding);
    converter = encoder.convert.bind(encoder);
  } else {
    const encoder = new TwoBytes(encoding);
    converter = encoder.convert.bind(encoder);
  }
  const fixedText = code.textContent.replace(/[^\p{ASCII}]+/gu, converter);
  let scriptMeta = document.querySelector("#meta");
  if (!scriptMeta) code.textContent = fixedText;
}

prepareDOM();
