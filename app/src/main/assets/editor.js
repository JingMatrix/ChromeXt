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

  if (!document.head) {
    window.addEventListener("DOMContentLoaded", prepareDOM);
    return;
  }
  document.head.appendChild(meta);
  document.head.appendChild(style);

  fixEncoding();
  renderEditor();
}

function fixEncoding() {
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

  const windows1252 = {
    // Code taken from https://github.com/mathiasbynens/windows-1252
    INDEX_BY_CODE_POINT: new Map([
      [129, 1],
      [141, 13],
      [143, 15],
      [144, 16],
      [157, 29],
      [160, 32],
      [161, 33],
      [162, 34],
      [163, 35],
      [164, 36],
      [165, 37],
      [166, 38],
      [167, 39],
      [168, 40],
      [169, 41],
      [170, 42],
      [171, 43],
      [172, 44],
      [173, 45],
      [174, 46],
      [175, 47],
      [176, 48],
      [177, 49],
      [178, 50],
      [179, 51],
      [180, 52],
      [181, 53],
      [182, 54],
      [183, 55],
      [184, 56],
      [185, 57],
      [186, 58],
      [187, 59],
      [188, 60],
      [189, 61],
      [190, 62],
      [191, 63],
      [192, 64],
      [193, 65],
      [194, 66],
      [195, 67],
      [196, 68],
      [197, 69],
      [198, 70],
      [199, 71],
      [200, 72],
      [201, 73],
      [202, 74],
      [203, 75],
      [204, 76],
      [205, 77],
      [206, 78],
      [207, 79],
      [208, 80],
      [209, 81],
      [210, 82],
      [211, 83],
      [212, 84],
      [213, 85],
      [214, 86],
      [215, 87],
      [216, 88],
      [217, 89],
      [218, 90],
      [219, 91],
      [220, 92],
      [221, 93],
      [222, 94],
      [223, 95],
      [224, 96],
      [225, 97],
      [226, 98],
      [227, 99],
      [228, 100],
      [229, 101],
      [230, 102],
      [231, 103],
      [232, 104],
      [233, 105],
      [234, 106],
      [235, 107],
      [236, 108],
      [237, 109],
      [238, 110],
      [239, 111],
      [240, 112],
      [241, 113],
      [242, 114],
      [243, 115],
      [244, 116],
      [245, 117],
      [246, 118],
      [247, 119],
      [248, 120],
      [249, 121],
      [250, 122],
      [251, 123],
      [252, 124],
      [253, 125],
      [254, 126],
      [255, 127],
      [338, 12],
      [339, 28],
      [352, 10],
      [353, 26],
      [376, 31],
      [381, 14],
      [382, 30],
      [402, 3],
      [710, 8],
      [732, 24],
      [8211, 22],
      [8212, 23],
      [8216, 17],
      [8217, 18],
      [8218, 2],
      [8220, 19],
      [8221, 20],
      [8222, 4],
      [8224, 6],
      [8225, 7],
      [8226, 21],
      [8230, 5],
      [8240, 9],
      [8249, 11],
      [8250, 27],
      [8364, 0],
      [8482, 25],
    ]),
    encodingError(mode) {
      if (mode === "replacement") {
        return 0xfffd;
      }
      // Else, `mode == 'fatal'`.
      throw new Error();
    },
    encode(input, options) {
      let mode;
      if (options && options.mode) {
        mode = options.mode.toLowerCase();
      }
      // Support `fatal` (default) and `replacement` error modes.
      if (mode !== "fatal" && mode !== "replacement") {
        mode = "fatal";
      }
      const length = input.length;
      const result = new Uint16Array(length);
      for (let index = 0; index < length; index++) {
        const codePoint = input.charCodeAt(index);
        // “If `code point` is an ASCII code point, return a byte whose
        // value is `code point`.”
        if (0x00 <= codePoint && codePoint <= 0x7f) {
          result[index] = codePoint;
          continue;
        }
        // “Let `pointer` be the index pointer for `code point` in index
        // single-byte.”
        if (this.INDEX_BY_CODE_POINT.has(codePoint)) {
          const pointer = this.INDEX_BY_CODE_POINT.get(codePoint);
          // “Return a byte whose value is `pointer + 0x80`.”
          result[index] = pointer + 0x80;
        } else {
          // “If `pointer` is `null`, return `error` with `code point`.”
          result[index] = this.encodingError(mode);
        }
      }
      return result;
    },
  };

  const code = document.querySelector("body > pre");
  const text = code.textContent;

  if (document.characterSet != "UTF-8" && !/^[\p{ASCII}]*$/u.test(text)) {
    let converter = () => new Uint8Array();
    if (document.characterSet == "windows-1252") {
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
