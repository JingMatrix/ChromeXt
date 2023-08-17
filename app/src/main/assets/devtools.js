window.addEventListener("load", () => {
  const meta = document.createElement("meta");
  meta.setAttribute("name", "viewport");
  meta.setAttribute("content", "width=device-width, initial-scale=1");
  document.head.prepend(meta);
  const style = document.createElement("style");
  style.innerText = ".filter-bitset-filter { overflow-x: scroll !important; }";
  document.body.prepend(style);
});

class WebSocket extends EventTarget {
  binaryType = "blob"; // Not used for DevTools protectol
  #state;
  #url;
  #payload;
  #pending = [];

  get bufferedAmount() {
    return new Blob(this.#pending).size;
  }
  get extensions() {
    return "";
  }
  get protocol() {
    return "";
  }
  get readyState() {
    return this.#state;
  }
  get url() {
    return this.#url;
  }

  constructor() {
    super();
    this.#state = 0;
    this.#url = arguments[0];
    this.#payload = { targetTabId: this.#url.split("/").pop() };
    ChromeXt.dispatch("websocket", this.#payload);
    ChromeXt.addEventListener("websocket", this.#handler.bind(this));
  }

  #handler(e) {
    const type = Object.keys(e.detail)[0];
    const data = e.detail[type];
    if (type == "message" && !("id" in data) && "params" in data) {
      const targetInfo = data.params.targetInfo;
      if (typeof targetInfo != "undefined" && targetInfo.type != "page") {
        console.info("Ignore inspecting", targetInfo.type, targetInfo.url);
        // To inspect them, we may need to change the targetTabId
        return;
      }
    } else if (type == "close") {
      this.close();
    } else if (type == "open") {
      this.#state = 1;
      // It would be better if the target is attached,
      // but there is no way to do so, neither to replay the pending message later.
    }
    const event = new MessageEvent(type, { data });
    try {
      this["on" + type](event);
    } catch {
      this.dispatchEvent(event);
    }
  }

  send(msg) {
    if (typeof msg == "string") {
      this.#pending.push(msg);
    } else {
      throw Error("Invalid message", msg);
    }
    if (this.#state == 1) {
      this.#pending.forEach((msg) => {
        this.#payload.message = msg;
        ChromeXt.dispatch("websocket", this.#payload);
      });
      this.#pending.length = 0;
    }
  }

  close() {
    this.#state = 2;
    const event = new MessageEvent("close");
    if (typeof this.onclose == "function") {
      this.onclose(event);
    } else {
      this.dispatchEvent(event);
    }
    this.#state = 3;
  }
}
