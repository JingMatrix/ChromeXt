const invalidChar = "�";

class Encoding {
  #name;
  decoder = new TextDecoder();
  get encoding() {
    return this.#name;
  }
  map = () => [];
  constructor(name = "utf-8") {
    this.#name = name.toLowerCase();
  }
  defaultOnError(_input, index, result) {
    result[index] = 0xff;
  }
  defaultOnAlloc = (len) => new Uint8Array(len);
  static generateTable() {
    return new Map();
  }
  encode(input, opt = {}) {
    if (!(this.table instanceof Map))
      Object.defineProperty(this, "table", {
        value: new Map([
          ...this.constructor.generateTable(this.decode.bind(this)),
          ...this.map(),
        ]),
      });
    if (this.encoding == "utf-8") return new TextEncoder().encode(input);
    const onError = opt.onError || this.defaultOnError.bind(this);
    const onAlloc = opt.onAlloc || this.defaultOnAlloc.bind(this);
    const length = input.length;
    const result = onAlloc(length);
    for (let i = 0; i < length; i++) {
      let charCode = input.charCodeAt(i);
      if (0x00 <= charCode && charCode < 0x80) {
        result[i] = charCode;
        continue;
      }
      if (charCode <= 0xdbff && charCode >= 0xd800) {
        // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String
        i++;
        charCode = charCode.toString(16) + input.charCodeAt(i).toString(16);
      }
      if (this.table.has(charCode)) {
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
  static generateTable(decode, start = 0x80, end = 0xff) {
    const range = [...Array(end - start + 1).keys()];
    const codePoints = new Uint8Array(range.map((x) => x + start));
    const str = decode(codePoints);
    console.assert(str.length == codePoints.length);
    return new Map(range.map((i) => [str.charCodeAt(i), codePoints[i]]));
  }
}

class TwoBytes extends Encoding {
  static intervals = [[0x81, 0xfe, 0x40, 0xfe]];
  static generateTable(decode) {
    const map = [];
    this.intervals.forEach(([b1Begin, b1End, b2Begin, b2End]) => {
      for (let b1 = b1Begin; b1 <= b1End; b1++) {
        for (let b2 = b2Begin; b2 <= b2End; b2++) {
          const code = (b2 << 8) | b1;
          const str = decode(new Uint16Array([code]));
          if (str.includes(invalidChar)) continue;
          let charCode = str.charCodeAt(0);
          if (charCode <= 0xdbff && charCode >= 0xd800) {
            charCode = charCode.toString(16) + str.charCodeAt(1).toString(16);
            map.push([charCode, code]);
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
  map = () => [["€".charCodeAt(0), 0x80]];
}

class SJIS extends TwoBytes {
  // https://en.wikipedia.org/wiki/Shift_JIS
  map = () => SingleByte.generateTable(this.decode.bind(this), 0xa1, 0xdf);
}

function fixEncoding() {
  // return false if failed
  const node = document.querySelector("body > pre");
  if (!node) return false;
  let text = node.textContent;
  if (document.characterSet == "UTF-8" || /^[\p{ASCII}]*$/u.test(text))
    return true;
  if (window.encoding) {
    if (!window.encoding.fixed) node.textContent = window.encoding["utf-8"];
    window.encoding.fixed = true;
    return true;
  }
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
  } else if (encoding == "shift_jis") {
    const encoder = new SJIS(encoding);
    converter = encoder.convert.bind(encoder);
  } else {
    const encoder = new TwoBytes(encoding);
    converter = encoder.convert.bind(encoder);
  }
  text = text.replace(/[^\p{ASCII}]+/gu, converter);
  const failed = text.includes(invalidChar);
  const url = window.location.href;
  node.textContent = text;
  if (failed && url.startsWith("http") && !url.endsWith(".js")) {
    fetch("")
      .then((res) => res.text())
      .then((t) => (node.textContent = t));
  }
  return !failed;
}
