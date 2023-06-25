function GM_xmlhttpRequest(details) {
  if (!details.url) {
    throw new Error("GM_xmlhttpRequest requires a URL.");
  }
  const uuid = Math.random();
  details.method = details.method ? details.method.toUpperCase() : "GET";
  ChromeXt(
    JSON.stringify({
      action: "xmlhttpRequest",
      payload: { id: GM_info.script.id, request: details, uuid },
    })
  );

  function base64ToBytes(base64) {
    const binString = atob(base64);
    return Uint8Array.from(binString, (m) => m.codePointAt(0));
  }

  function base64ToBlob(base64, type) {
    return new Blob([base64ToBytes(base64).buffer], { type });
  }

  function base64ToUTF8(base64) {
    return new TextDecoder().decode(base64ToBytes(base64));
  }

  function bytesToBase64(bytes) {
    const binString = Array.from(bytes, (x) => String.fromCodePoint(x)).join(
      ""
    );
    return btoa(binString);
  }

  if ("binary" in details && "data" in details && details.binary) {
    switch (details.data.constructor) {
      case File:
      case Blob:
        details.data = details.data.arrayBuffer();
      case ArrayBuffer:
        details.data = new Uint8Array(details.data);
      case Uint8Array:
        data = bytesToBase64(data);
        break;
      default:
        details.binary = false;
    }
  }

  window.addEventListener("xmlhttpRequest", (e) => {
    if (e.detail.id == GM_info.script.id && e.detail.uuid == uuid) {
      let data = e.detail.data;
      switch (e.detail.type) {
        case "load":
          data.readyState = 4;
          data.finalUrl = data.responseHeaders.Location || details.url;
          if ("overrideMimeType" in details)
            data.responseHeaders["Content-Type"] = details.overrideMimeType;
          if ("responseType" in details) {
            const base64 = data.responseText;
            const type = data.responseHeaders["Content-Type"] || "";
            switch (details.responseType) {
              case "arraybuffer":
                data.response = base64ToBytes(base64).buffer;
                break;
              case "blob":
                data.response = base64ToBlob(base64, type);
                break;
              case "stream":
                data.response = base64ToBlob(base64, type).stream();
                break;
              case "json":
                data.response = JSON.parse(base64ToUTF8(base64));
                break;
              default:
                data.response = atob(base64);
            }
          } else {
            data.responseText = base64ToUTF8(data.responseText);
            data.response = data.responseText;
          }
          details.onload(data);
          break;
        case "error":
          details.onerror(data);
          break;
        case "abort":
          details.onabort(data);
          break;
        case "timeout":
          details.ontimeout(data);
          break;
        default:
          console.log(e.detail);
      }
    }
  });
  return {
    abort: () => {
      ChromeXt(JSON.stringify({ action: "abortRequest", payload: uuid }));
    },
  };
}
