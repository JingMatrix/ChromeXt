const ChromeXt = globalThis.ChromeXt.unlock(ChromeXtUnlockKeyForEruda, false);

eruda._initDevTools = new Proxy(eruda._initDevTools, {
  hooked: false,
  bypassTrustedTypes() {
    if (this.hooked) return;
    this.hooked = true;
    let stubHTMLPolicy;
    try {
      stubHTMLPolicy = trustedTypes.createPolicy("eruda", {
        createHTML: (s) => s,
      });
    } catch {
      stubHTMLPolicy = trustedTypes.polices.values().next().value;
      stubHTMLPolicy.bypass = true;
    }
    const _insertAdjacentHTML = HTMLElement.prototype.insertAdjacentHTML;
    HTMLDivElement.prototype.insertAdjacentHTML = function (p, t) {
      return _insertAdjacentHTML.apply(this, [p, stubHTMLPolicy.createHTML(t)]);
    };
    const _html = eruda._$el.__proto__.html;
    eruda._$el.__proto__.html = function (t) {
      return _html.apply(this, [stubHTMLPolicy.createHTML(t)]);
    };
    const _enable = eruda.chobitsu.domain("Overlay").enable;
    eruda.chobitsu.domain("Overlay").enable = function () {
      if (_enable.enabled) return;
      _enable.enabled = true;
      _enable.apply(this, arguments);
      const overlay =
        eruda._container.parentNode.querySelector(
          ".__chobitsu-hide__"
        ).shadowRoot;
      const tooltip = overlay.querySelector("div.luna-dom-highlighter > div");
      Object.defineProperty(tooltip, "innerHTML", {
        set(value) {
          this.setHTML(value);
        },
      });
    };
  },
  apply(target, thisArg, args) {
    this.bypassTrustedTypes();
    return target.apply(thisArg, args);
  },
});

eruda._initStyle = new Proxy(eruda._initStyle, {
  addStyle(id, content) {
    const erudaRoot = eruda._shadowRoot;
    if (erudaRoot.querySelector("style#" + id)) return;
    const style = document.createElement("style");
    style.id = id;
    style.setAttribute("type", "text/css");
    style.textContent = content;
    erudaRoot.append(style);
  },
  apply(target, thisArg, args) {
    const result = target.apply(thisArg, args);
    this.addStyle("new_icons", eruda._styles[1]);
    this.addStyle("eruda_dom_fix", eruda._styles[2]);
    this.addStyle("plugin", eruda._styles[3]);
    const catchCSP = (e) => {
      if (!e.sourceFile.endsWith("eruda.js")) return;
      e.stopImmediatePropagation();
      if (e.blockedURI == "data" && e.violatedDirective == "font-src") {
        eruda._replaceFont = true;
        this.addStyle("font_fix", eruda._styles[0]);
        document.removeEventListener("securitypolicyviolation", catchCSP);
      } else if (e.blockedURI == "inline" && e.target == eruda._container) {
        console.error("Impossible to load Eruda");
      }
    };
    if (typeof eruda._replaceFont == "undefined") {
      eruda._replaceFont = false;
      document.addEventListener("securitypolicyviolation", catchCSP);
    } else if (eruda._replaceFont) {
      this.addStyle("font_fix", eruda._styles[0]);
    }
	return result;
  },
});

class Filter {
  constructor(selector) {
    this._$el = selector;
  }
  #filter = new Array(...ChromeXt.filters);
  #write() {
    ChromeXt.filters.sync(this.#filter);
  }
  add(rule) {
    if (typeof rule == "string") {
      rule = rule.trim();
      if (rule != "" && !this.#filter.includes(rule)) {
        this.#filter.push(rule);
        ChromeXt.filters.push(rule);
      }
    }
  }
  get() {
    return this.#filter;
  }
  remove(rule) {
    this.#filter = this.#filter.filter((item) => item.trim() !== rule);
  }
  new() {
    this.#filter.push("");
  }
  save() {
    this.#filter = [];
    Array.from(this._$el.find(".eruda-filter-item")).forEach((it) =>
      this.#filter.push(it.innerText.trim())
    );
    this.remove("");
    this.#write();
  }
}

const c = eruda.util.classPrefix;
const s = (spans) =>
  spans
    .map((e) => `<span class="${c("icon-" + e + " " + e)}"></span>`)
    .join("");

class elements extends eruda.Elements {
  constructor() {
    super();
    this._deleteNode = () => {
      const node = this._curNode;
      const selector = this.generateQuerySelector(node);
      this._container.get("resources")._filter.add(selector);
      if (node.parentNode) {
        node.parentNode.removeChild(node);
      }
    };
  }
  static generateQuerySelector(el) {
    if (el.tagName.toLowerCase() == "html") return "html";
    let str = el.tagName.toLowerCase();
    if (el.id != "") {
      str += "#" + el.id;
    } else if (el.className != "") {
      let classes = el.className
        .split(/\s/)
        .filter((rule) => rule.trim() != "");
      str += "." + classes.join(".");
    } else {
      return this.generateQuerySelector(el.parentNode) + " > " + str;
    }
    return str;
  }
}

class resources extends eruda.Resources {
  _initTpl() {
    super._initTpl();
    this._$el.prepend(`<div class="${c("section commands")}"></div>`);
    this._$el.prepend(`<div class="${c("section filters")}"></div>`);
    this._$filter = this._$el.find(".eruda-filters.eruda-section");
    this._$command = this._$el.find(".eruda-commands.eruda-section");
    this._filter = new Filter(this._$filter);
  }
  _bindEvent() {
    super._bindEvent();
    this._$el
      .on("click", ".eruda-delete-filter", (e) => {
        const rule = e.curTarget.previousSibling.textContent;
        this._filter.remove(rule);
        this.refreshFilter();
      })
      .on("click", ".eruda-add-filter", () => {
        this._filter.new();
        this.refreshFilter();
        this._$filter.find(".eruda-filter-item").last()[0].focus();
      })
      .on("click", ".eruda-save-filter", () => {
        this._filter.save();
        this.refreshFilter();
        this._container.notify("Filter Saved");
      })
      .on("click", ".eruda-command", (e) => {
        const index = e.curTarget.dataset.index;
        this._command[index].listener(e);
        eruda.hide();
      });
  }
  refresh() {
    return super.refresh().refreshFilter().refreshCommand();
  }
  refreshCommand() {
    this._command = ChromeXt.commands.filter((m) => m.enabled);
    const commands = this._command
      .map(
        (cmd, index) =>
          `<span data-index=${index} class="${c("command")}">${
            cmd.title
          }</span>`
      )
      .join("");
    this._$command.html(
      `<h2 class="${c("title")}">UserScript Commands</h2>` +
        `<div class="${c("commands")}">${commands}</div>`
    );
    return this;
  }
  refreshFilter() {
    let filterHtml = "<li></li>";
    const filters = this._filter.get();
    const spanItem = `span contentEditable="true" class="${c("filter-item")}"`;
    const spanDel = `span class="${c("icon-delete delete-filter")}"`;
    if (filters.length > 0) {
      filterHtml = filters
        .map((key) => `<li><${spanItem}>${key}</span><${spanDel}></span></li>`)
        .join("");
    }
    const div = (e) =>
      `<div class="${c("btn " + e + "-filter")}">${s([e])}</div>`;
    this._$filter.html(
      `<h2 class="${c("title")}">Cosmetic Filters` +
        div("save") +
        div("add") +
        `</h2><ul>${filterHtml}</ul>`
    );
    return this;
  }
}

class info extends eruda.Info {
  add(name, val, cls = { span: ["copy"] }) {
    if (!Array.isArray(this._infos)) {
      this._infos[name] = { val, cls };
    } else {
      this._infos.push({ name, val, cls });
      this._render();
    }
  }
  _addDefInfo() {
    this._infos = {};
    this.add(
      "UserScripts",
      '<input type="file" multiple id="new_script" accept="text/javascript" style="display:none"/>',
      { li: "userscripts", span: ["add"] }
    );
    const spanScript = `span class="${c("script")}"`;
    this._infos["UserScripts"].val += ChromeXt.scripts
      .map(
        ({ script }, index) =>
          `<${spanScript} data-index=${index}>${script.name}</span>`
      )
      .join("");
    this.add(
      "User CSP rules",
      ChromeXt.cspRules.length > 0 ? ChromeXt.cspRules.join(" | ") + " | " : "",
      {
        li: "csp-rules",
        span: [ChromeXt.cspRules.length == 0 ? "add" : "save"],
      }
    );
    super._addDefInfo();
    delete this._infos["Backers"];
    this._infos["User Agent"].cls = {
      li: "user-agent",
      span: ["save", "reset"],
    };
    this._infos["About"].val = `<div class="${c("check-update")}">Eruda v${
      eruda.version
    }</div>`;
    this._infos = Object.entries(this._infos).map(([k, v]) => {
      return { name: k, ...v };
    });
    this._infos.splice(2, 2, this._infos[3], this._infos[2]);
    this._render();
  }
  _render() {
    const infos = [];
    this._infos.forEach(({ name, val, cls }) => {
      val = typeof val == "function" ? val() : val;
      let html = {};
      html.li = cls.li ? "li class='" + c(cls.li) + "'" : "li";
      html.h2 = name + s(cls.span);
      infos.push({ name, val, html });
    });
    const html = infos
      .map(
        (info) =>
          `<${info.html.li}><h2 class="${c("title")}">${info.html.h2}</h2>` +
          `<div class="${c("content")}">${info.val}</div></li>`
      )
      .join("");
    this._renderHtml("<ul>" + html + "</ul>");
  }
  _bindEvent() {
    super._bindEvent();
    this._$el.find(".eruda-user-agent > div")[0].contentEditable = true;
    this._$el
      .on("click", ".eruda-user-agent .eruda-icon-save", (e) => {
        this._container.notify("User-Agent config saved");
        e.stopPropagation();
        ChromeXt.dispatch("syncData", {
          origin: window.location.origin,
          name: "userAgent",
          data: this._$el.find(".eruda-user-agent > div").text(),
        });
      })
      .on("click", ".eruda-user-agent .eruda-icon-reset", (_e) => {
        this._container.notify("User-Agent restored");
        ChromeXt.dispatch("syncData", {
          origin: window.location.origin,
          name: "userAgent",
        });
      })
      .on("click", ".eruda-csp-rules .eruda-icon-add", (_e) => {
        this._$el.find(".eruda-csp-rules > h2 > span")[0].className =
          c("icon-save save");
        const editor = this._$el.find(".eruda-csp-rules > div")[0];
        editor.contentEditable = true;
        editor.focus();
      })
      .on("click", ".eruda-csp-rules .eruda-icon-save", (e) => {
        this._container.notify("CSP Rules config saved");
        e.stopPropagation();
        const rules = this._$el.find(".eruda-csp-rules > div").text() || "";
        ChromeXt.cspRules.sync(rules.split(" | ").filter((r) => r.length > 0));
      })
      .on("click", ".eruda-userscripts .eruda-script", (e) => {
        const sources = this._container.get("sources");
        if (!sources) return;
        const index = e.curTarget.dataset.index;
        sources.set("object", ChromeXt.scripts[index].script);
        this._container.showTool("sources");
      })
      .on("click", ".eruda-userscripts .eruda-add", (_e) => {
        this._$el.find("#new_script")[0].click();
      })
      .on("click", ".eruda-check-update", (_e) => {
        ChromeXt.dispatch("updateEruda");
      })
      .on("change", "#new_script", (e) => {
        Array.from(e.curTarget.files).forEach((f) => {
          if (f.name.endsWith(".user.js")) {
            f.text().then((s) => {
              ChromeXt.dispatch("installScript", s);
              this._container.notify("Installing " + f.name);
            });
          } else {
            this._container.notify(f.name + " is not a UserScript file.");
          }
        });
      });
  }
}

eruda.Elements = elements;
eruda.Resources = resources;
eruda.Info = info;

if (typeof define == "function" && define.amd === false) define.amd = true;
eruda.init();
eruda.show();
