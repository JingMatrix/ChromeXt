const ChromeXt = Symbol.ChromeXt.unlock(ChromeXtUnlockKeyForEruda, false);

eruda._inLocalPage =
  ["content://", "file://"].includes(location.origin) ||
  location.pathname.endsWith(".txt");

eruda._initDevTools = new Proxy(eruda._initDevTools, {
  getHeight(node, prop) {
    return Number(getComputedStyle(node)[prop].slice(0, -2));
  },
  eruda_h: 5,
  fixTop() {
    const btn_t = this.getHeight(eruda._entryBtn._$el[0], "top");
    if (window.innerHeight - btn_t < 150) return -2;
    if (this.eruda_h < btn_t + 100) {
      return btn_t + 100 - this.eruda_h;
    } else {
      return 0;
    }
  },
  hookToggle(devTools) {
    const _show = devTools.show;
    devTools.show = (...args) => {
      if (this.eruda_h == 5) return _show.apply(devTools, args);
      const el = devTools._$el[0];
      const resizer = devTools._$el.find(".eruda-resizer")[0];
      const top = this.fixTop();
      if (top > -1 && top + this.eruda_h / 2 < window.innerHeight / 2) {
        el.style.bottom = "";
        el.style.top = top + "px";
        resizer.style.height = "";
      } else {
        el.style.top = "";
        resizer.style.height = "10px";
        if (top > 0 && top < window.innerHeight) {
          el.style.bottom = window.innerHeight - top - this.eruda_h + "px";
        } else {
          el.style.bottom = "";
        }
      }
      return _show.apply(devTools, args);
    };
    const _hide = devTools.hide;
    devTools.hide = (...args) => {
      const el = devTools._$el[0];
      this.eruda_h = this.getHeight(el, "height");
      return _hide.apply(devTools, args);
    };
  },
  typesHooked: false,
  bypassTrustedTypes() {
    if (this.typesHooked) return;
    let stubHTMLPolicy;
    try {
      stubHTMLPolicy = trustedTypes.createPolicy("eruda", {
        createHTML: (s) => s,
      });
    } catch {
      if (typeof Element.prototype.setHTML != "function") return;
    }
    const _insertAdjacentHTML = HTMLElement.prototype.insertAdjacentHTML;
    HTMLDivElement.prototype.insertAdjacentHTML = function (p, t) {
      if (stubHTMLPolicy != undefined) {
        return _insertAdjacentHTML.apply(this, [
          p,
          stubHTMLPolicy.createHTML(t),
        ]);
      } else {
        const div = document.createElement("div");
        div.setHTML(t);
        return this.insertAdjacentElement(p, div.children[0]);
      }
    };
    const _html = eruda._$el.__proto__.html;
    eruda._$el.__proto__.html = function (t) {
      if (stubHTMLPolicy != undefined) {
        return _html.apply(this, [stubHTMLPolicy.createHTML(t)]);
      } else {
        for (const node of this) node.setHTML(t);
      }
    };
    this.typesHooked = true;
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
          if (this.innerHTML == value) return true;
          try {
            this.setHTML(value);
          } catch {
            this.textContent = value;
          }
          return true;
        },
      });
    };
  },
  apply(target, thisArg, args) {
    this.bypassTrustedTypes();
    const result = target.apply(thisArg, args);
    this.hookToggle(eruda._devTools);
    return result;
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
    let meta = document.querySelector("meta[name='viewport']");
    if (eruda._inLocalPage && !meta) {
      meta = document.createElement("meta");
      meta.setAttribute("name", "viewport");
      meta.setAttribute("content", "initial-scale=1");
      document.head.prepend(meta);
    }
    const result = target.apply(thisArg, args);
    this.addStyle("new_icons", eruda._styles[1]);
    this.addStyle("dom_fix", eruda._styles[2]);
    this.addStyle("plugin", eruda._styles[3]);
    if (typeof eruda._replaceFont == "undefined") {
      const catchCSP = (e) => {
        if (!e.sourceFile.endsWith("eruda.js")) return;
        e.stopImmediatePropagation();
        if (e.blockedURI == "data" && e.violatedDirective == "font-src") {
          eruda._replaceFont = true;
          this.addStyle("font_fix", eruda._styles[0]);
          document.removeEventListener("securitypolicyviolation", catchCSP);
        } else if (e.blockedURI == "inline" && e.target == eruda._container) {
          document.removeEventListener("securitypolicyviolation", catchCSP);
          throw new Error(
            "Eruda blocked by " + e.effectiveDirective + " " + e.originalPolicy
          );
        }
      };
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

function c(str) {
  const prefix = `eruda-`;
  return str
    .trim()
    .split(/\s+/)
    .map((singleClass) => {
      if (singleClass.includes(prefix)) {
        return singleClass;
      }
      return singleClass.replace(/[\w-]+/, (match) => `${prefix}${match}`);
    })
    .join(" ");
}

const s = (spans) =>
  spans
    .map((e) => `<span class="${c("icon-" + e + " " + e)}"></span>`)
    .join("");

eruda.Elements = class extends eruda.Elements {
  constructor() {
    super();
    this._deleteNode = () => {
      const node = this._curNode;
      const selector = this.getSelector(node);
      this._container.get("resources")._filter.add(selector);
      if (node.parentNode) {
        node.parentNode.removeChild(node);
      }
    };
  }
  getSelector(el, useSilbling = true) {
    if (document.documentElement === el) return "html";
    let str = el.tagName.toLowerCase();
    if (el.id != "") return str + "#" + el.id;
    let classes = Array.from(el.classList);
    if (classes.length > 0) str += "." + classes.join(".");
    if (classes.length > 1) return str;
    let prev = el.previousSibling;
    if (useSilbling) {
      while (prev instanceof Text) prev = prev.previousSibling;
      if (
        prev instanceof HTMLElement &&
        prev.tagName &&
        (prev.classList.length > 0 || prev.id != "")
      )
        return this.getSelector(prev, false) + " + " + str;
    }
    return this.getSelector(el.parentNode, useSilbling) + " > " + str;
  }
};

eruda.Sources = class extends eruda.Sources {
  _renderDef() {
    if (this._html && this._data) {
      return this._render();
    }
    if (eruda._inLocalPage) {
      this._html = document.body.innerText;
      this._data = { type: "raw", val: this._html };
      return this._renderDef();
    }
    if (this._isGettingHtml) return;
    this._isGettingHtml = true;

    const setData = () => {
      this._data = { type: "html", val: this._html };
      this._isGettingHtml = false;
      return this._renderDef();
    };
    fetch(location.href, { cache: "force-cache", mode: "same-origin" })
      .then((res) => res.text())
      .then((text) => {
        this._html = text;
        setData();
      })
      .catch((e) => {
        console.error(e);
        this._html = "Sorry, unable to fetch source code:(";
        setData();
      });
  }
};

eruda.Resources = class extends eruda.Resources {
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
        const hide = this._command[index].listener(e);
        if (hide !== false) eruda.hide();
        this.refreshCommand();
      });
  }
  refresh() {
    return super.refresh().refreshFilter().refreshCommand();
  }
  refreshCommand() {
    this._command = ChromeXt.commands.filter((m) => m.enabled);
    const commands = this._command
      .map(function (cmd, index) {
        let title = cmd.title.toString();
        if (typeof cmd.title == "function") {
          title = cmd.title(index);
        }
        return `<span data-index=${index} class="${c("command")}">${title}</span>`;
      })
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
    const spanItem = `span contenteditable="true" class="${c("filter-item")}"`;
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
};

eruda.Info = class extends eruda.Info {
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
      '<input type="file" multiple id="new_script" accept="text/javascript,application/javascript" style="display:none"/>',
      { li: "userscripts", span: ["add", "eye"] }
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
    if (ChromeXt.cspRules.length > 0)
      this._$el.find(".eruda-csp-rules > div")[0].contentEditable = true;
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
      .on("click", ".eruda-userscripts .eruda-icon-eye", (_e) => {
        window.open("https://jingmatrix.github.io/ChromeXt/");
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
};

if (typeof define == "function" && define.amd === false) define.amd = true;
eruda.init();
eruda.show();
