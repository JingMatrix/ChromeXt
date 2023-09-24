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
  defaultOnError(_input, result) {
    result.push(0xff);
  }
  defaultOnAlloc = (data) => new Uint8Array(data);
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
    const result = [];
    [...input].forEach((str) => {
      let codePoint = str.codePointAt(0);
      if (0x00 <= codePoint && codePoint < 0x80) {
        result.push[codePoint];
        return;
      }
      if (this.table.has(codePoint)) {
        result.push(this.table.get(codePoint));
      } else if (str == invalidChar) {
        const ret = onError(input, result);
        if (ret === -1) {
          throw Error("Stop decoding", input);
        }
      }
    });
    return new Uint8Array(onAlloc(result).buffer).filter((c) => c != 0x00);
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
    const charCodes = new Uint8Array(range.map((x) => x + start));
    const str = decode(charCodes);
    console.assert(str.length == charCodes.length);
    return new Map(range.map((i) => [str.codePointAt(i), charCodes[i]]));
  }
}

class TwoBytes extends Encoding {
  static intervals = [[0x81, 0xfe, 0x40, 0xfe]];
  static generateTable(decode) {
    const map = [];
    this.intervals.forEach(([b1Begin, b1End, b2Begin, b2End]) => {
      for (let b1 = b1Begin; b1 <= b1End; b1++) {
        for (let b2 = b2Begin; b2 <= b2End; b2++) {
          const charCode = (b2 << 8) | b1;
          const str = decode(new Uint16Array([charCode]));
          if (!str.includes(invalidChar))
            map.push([str.codePointAt(0), charCode]);
        }
      }
    });
    return map;
  }
  defaultOnAlloc = (data) => new Uint16Array(data);
}

class GBK extends TwoBytes {
  // https://en.wikipedia.org/wiki/GBK_(character_encoding)
  map = () => [["€".codePointAt(0), 0x80]];
}

class SJIS extends TwoBytes {
  // https://en.wikipedia.org/wiki/Shift_JIS
  map = () => SingleByte.generateTable(this.decode.bind(this), 0xa1, 0xdf);
}

function preferUTF8(
  text,
  utf8,
  encoding = document.characterSet.toLowerCase()
) {
  // Check if text with given encoding is properly encodes;
  // The argmuent utf8 is the same data encoded with UTF-8;
  // Return true if we should discard given encoding and use UTF-8 encoding instead
  if (encoding == "utf-8") return false;
  const encoded = new TextDecoder(encoding).decode(
    new TextEncoder().encode(utf8)
  );
  const length = Math.min(text.length, encoded.length);
  const result = text.slice(0, length) == encoded.slice(0, length);
  const msg = "The declared encoding is " + (result ? "incorrect" : "correct");
  console.debug(msg);
  return result;
}

function fixEncoding(tryPart = false, tryFetch = true, node) {
  // return false if failed
  node = node || document.querySelector("body > pre");
  const url = window.location.href;
  if (!node) return false;
  const text = node.textContent;
  const encoding = document.characterSet.toLowerCase();
  if (
    url.startsWith("file://") ||
    document.characterSet == "UTF-8" ||
    /^[\p{ASCII}]*$/u.test(text)
  )
    return true;
  if (window.content) {
    if (!window.content.fixed) {
      const utf8 = window.content["utf-8"];
      if (preferUTF8(text, utf8, encoding)) node.textContent = utf8;
    }
    window.content.fixed = true;
    return true;
  }
  let converter = () => invalidChar;
  let encoder = null;
  if (
    encoding.startsWith("windows") ||
    encoding.startsWith("iso-8859") ||
    encoding.startsWith("koi") ||
    encoding.startsWith("ibm") ||
    encoding.includes("mac")
  ) {
    encoder = new SingleByte(encoding);
  } else if (encoding.startsWith("gb")) {
    encoder = new GBK(encoding);
  } else if (encoding == "shift_jis") {
    encoder = new SJIS(encoding);
  } else {
    encoder = new TwoBytes(encoding);
  }
  if (encoder !== null) converter = encoder.convert.bind(encoder);
  let failed, converted;
  if (!tryPart && text.includes(invalidChar)) {
    failed = true;
  } else {
    converted = text.replace(/[^\p{ASCII}]+/gu, converter);
    failed = converted.includes(invalidChar);
  }
  if (!failed || tryPart) node.textContent = converted;
  if (tryFetch && failed) {
    return new Promise((resolve, _reject) => {
      fetch(url, { cache: "force-cache", mode: "same-origin" })
        .then((res) => res.text())
        .then((utf8) => {
          node.textContent = preferUTF8(text, utf8, encoding) ? utf8 : text;
          resolve(true);
        })
        .catch((_e) => resolve(false));
    });
  } else {
    return !failed;
  }
}
