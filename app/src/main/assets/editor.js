const invalidChar = "�";
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

function renderEditor(code) {
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

  if (code.textContent.includes(invalidChar)) {
    const msg =
      "Current script may contain badly decoded text.\n\nTo fix possible issues, you can change the browser UI language to English.";
    createDialog(msg, false);
  } else {
    const msg =
      "Code editor is blocked on this page.\n\nPlease use page menu to install this UserScript, or reload current page to enable the editor.";
    createDialog(msg);
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

function createDialog(msg, remove = true) {
  const dialog = document.createElement("dialog");
  dialog.id = "confirm";
  dialog.textContent = msg;
  document.body.prepend(dialog);
  dialog.show();
  if (remove) setTimeout(fixDialog);
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
  constructor(name = "utf-8") {
    this.#name = name.toLowerCase();
    Object.defineProperty(this, "table", { value: this.generateTable() });
  }
  defaultOnError(_input, _index, _result) {
    return 0xff;
  }
  defaultOnAlloc = (len) => new Uint8Array(len);
  generateTable() {
    return new Map();
  }
  encode(input, opt = {}) {
    if (this.encoding == "utf-8") return new TextEncoder().encode(input);
    const onError = opt.onError || this.defaultOnError.bind(this);
    const onAlloc = opt.onAlloc || this.defaultOnAlloc.bind(this);
    const length = input.length;
    const result = onAlloc(length);
    for (let i = 0; i < length; i++) {
      const codePoint = input.charCodeAt(i);
      if (0x00 <= codePoint && codePoint < 0x80) {
        result[i] = codePoint;
      } else if (this.table.has(codePoint)) {
        result[i] = this.table.get(codePoint);
      } else {
        const ret = onError(input, i, result);
        if (ret === -1) {
          break;
        }
      }
    }
    if (!(result instanceof Uint8Array)) {
      return new Uint8Array(result.buffer).filter((c) => c != 0x00);
    } else {
      return result;
    }
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

class GBK extends Encoding {
  generateTable() {
    // https://en.wikipedia.org/wiki/GBK_(character_encoding)#Encoding
    const range = [...Array(23940).keys()];
    const codePoints = new Uint16Array(23940);
    const intervals = [
      [0xa1, 0xa9, 0xa1, 0xfe],
      [0xb0, 0xf7, 0xa1, 0xfe],
      [0x81, 0xa0, 0x40, 0xfe],
      [0xaa, 0xfe, 0x40, 0xa0],
      [0xa8, 0xa9, 0x40, 0xa0],
      [0xaa, 0xaf, 0xa1, 0xfe],
      [0xf8, 0xfe, 0xa1, 0xfe],
      [0xa1, 0xa7, 0x40, 0xa0],
    ];
    let i = 0;
    for (const [b1Begin, b1End, b2Begin, b2End] of intervals) {
      for (let b2 = b2Begin; b2 <= b2End; b2++) {
        if (b2 !== 0x7f) {
          for (let b1 = b1Begin; b1 <= b1End; b1++) {
            codePoints[i++] = (b2 << 8) | b1;
          }
        }
      }
    }
    const str = this.decode(codePoints);
    const map = new Map(range.map((i) => [str.charCodeAt(i), codePoints[i]]));
    map.set("€".charCodeAt(0), 0x80);
    return map;
  }
  replacement = new TextEncoder().encode(invalidChar);
  defaultOnError(_input, index, result) {
    // Find last invalid utf-8 encoding
    index = (index - 1) * (result.byteLength / result.length);
    let codePoints = new Uint8Array(result.buffer, index, 1);
    while (codePoints[0] < 0b11100000) {
      index = index - 1;
      codePoints = new Uint8Array(result.buffer, index, 1);
    }
    // Replace it
    codePoints = new Uint8Array(result.buffer, index, this.replacement.length);
    this.replacement.forEach((c, i) => (codePoints[i] = c));
  }
  defaultOnAlloc = (len) => new Uint16Array(len);
}

function fixEncoding(code) {
  let converter = () => invalidChar;
  const encoding = document.characterSet.toLowerCase();
  if (
    encoding.startsWith("windows") ||
    encoding.startsWith("iso-8859") ||
    encoding.includes("mac")
  ) {
    const encoder = new SingleByte(encoding);
    converter = encoder.convert.bind(encoder);
  } else if (encoding.startsWith("gb")) {
    const encoder = new GBK(encoding);
    converter = encoder.convert.bind(encoder);
  }
  code.textContent = code.textContent.replace(/[^\p{ASCII}]+/gu, converter);
}

prepareDOM();
