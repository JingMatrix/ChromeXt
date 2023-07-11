function retryEruda(message) {
  if (retryingEruda) {
    return;
  }
  retryingEruda = true;
  message += ", a reload might unblock it.\n\nDo you want to try it?";
  if (confirm(message)) {
    location.reload();
  }
}

let retryingEruda = false;

if (typeof eruda != "undefined" && typeof eruda._configured == "undefined") {
  class Filter {
    #filter = [];
    constructor() {}
    #write() {
      let payload = { origin: window.location.origin };
      if (this.#filter.length > 0) {
        payload.data = this.#filter;
      }
      ChromeXt(
        JSON.stringify({
          action: "cosmeticFilter",
          payload,
        })
      );
    }
    parseFilter(filter) {
      if (typeof filter == "string") {
        filter = JSON.parse(filter);
        if (Array.isArray(filter)) {
          this.#filter = filter;
        }
      }
    }
    add(rule) {
      if (typeof rule == "string") {
        rule = rule.trim();
        if (rule != "" && !this.#filter.includes(rule)) {
          this.#filter.push(rule);
          this.#write();
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
    save(container) {
      this.#filter = [];
      container
        .querySelectorAll(".eruda-filter-item")
        .forEach((it) => this.#filter.push(it.innerText.trim()));
      this.remove("");
      this.#write();
    }
  }

  eruda._filter = new Filter();
  class elements extends eruda.Elements {
    constructor() {
      super();
      eruda._filter.parseFilter(ChromeXt.filters);
      this._deleteNode = () => {
        const node = this._curNode;
        const selector = generateQuerySelector(node);
        eruda._filter.add(selector);
        if (node.parentNode) {
          node.parentNode.removeChild(node);
        }
      };
    }
  }

  function c(str) {
    return str
      .split(" ")
      .map((it) => "eruda-" + it)
      .join(" ");
  }

  class resources extends eruda.Resources {
    _bindEvent() {
      super._bindEvent();
      const $el = this._$el;
      const self = this;
      const container = this._container;
      $el
        .on("click", ".eruda-delete-filter", function (e) {
          const rule = e.curTarget.dataset.key;
          eruda._filter.remove(rule);
          self.refreshFilter();
        })
        .on("click", ".eruda-new-filter", () => {
          eruda._filter.save(this._$ChromeXtFilter[0]);
          eruda._filter.new();
          self.refreshFilter();
          this._$ChromeXtFilter.find(".eruda-filter-item").last()[0].focus();
        })
        .on("click", ".eruda-save-filter", () => {
          eruda._filter.save(this._$ChromeXtFilter[0]);
          self.refreshFilter();
          container.notify("Filter Saved");
        })
        .on("click", ".eruda-command", (e) => {
          const index = e.curTarget.dataset.index;
          ChromeXt.commands[index].listener(e);
          eruda.hide();
        });
    }
    _initTpl() {
      super._initTpl();
      if (typeof ChromeXt.commands != "undefined") {
        this._$el.prepend(`<div class="${c("section commands")}">
<h2 class="${c(
          "title"
        )}" style="width: 100%;">UserScript Commands</h2><div class="${c(
          "commands"
        )}">${ChromeXt.commands
          .filter((m) => m.enabled)
          .map(
            (command, index) =>
              `<span data-index=${index} class="${c("command")}">${
                command.title
              }</span>`
          )
          .join("")}</div>
</div>`);
      }
      this._$el.prepend(`<div class="${c("section filters")}"></div>`);
      this._$ChromeXtFilter = this._$el.find(".eruda-filters.eruda-section");
    }
    refresh() {
      return super.refresh().refreshFilter();
    }

    refreshFilter() {
      let filterHtml = "<li></li>";

      let filter = eruda._filter.get();
      if (filter.length > 0) {
        filterHtml = filter
          .map(
            (key) => `<li style="display: flex;">
          <span style="width:90%;padding:0.3em;" contentEditable="true" class="${c(
            "filter-item"
          )}">${key}</span>
		  <span style="width:10%;margin:auto;text-align:center;" class="${c(
        "icon-delete delete-filter"
      )}" data-key="${key}"></span>
        </li>`
          )
          .join("");
      }

      this._$ChromeXtFilter.html(`<h2 class="${c("title")}">Cosmetic Filters
			<div class="${c("btn save-filter")}"><span class="${c(
        "icon icon-save"
      )}"></span></div>
			<div class="${c("btn new-filter")}"><span class="${c(
        "icon icon-add"
      )}"></span></div>
		</h2>
		<ul>${filterHtml}</ul>
      `);
      return this;
    }
  }
  class info extends eruda.Info {
    _render() {
      const infos = this._infos;
      this._infos = infos.filter((info) => info.name != "User Agent");
      super._render();
      this._infos = infos;
    }
    _renderHtml(html) {
      if (html.startsWith("<ul>")) {
        super._renderHtml(
          `<ul><li class="${c("user-agent")}"><h2 class="${c(
            "title"
          )}">User Agent<span class="${c(
            "icon-save save"
          )}"></span><span class="${c(
            "icon-reset reset"
          )}"></span></h2><div class="${c("content")}" contenteditable="true">${
            navigator.userAgent
          }</div></li><li class="${c("csp-rules")}"><h2 class="${c(
            "title"
          )}">User CSP Rules<span class="${c(
            "icon-save save"
          )}"></span></h2><div class="${c("content")}" contenteditable="true">${
            ChromeXt.cspRules
          }</div></li><li class="${c("userscripts")}"><h2 class="${c(
            "title"
          )}">UserScripts<span class="${c(
            "icon-add add"
          )}"></span><input type="file" multiple id="new_script" accept="text/javascript" style="display:none"/></h2><div class="${c(
            "content"
          )}">${(ChromeXt.scripts || [])
            .map(
              (info, index) =>
                `<span data-index=${index} class="${c("script")}">${
                  info.script.name
                }</span>`
            )
            .join("")}</div></li>` + html.substring(4)
        );
      } else {
        super._renderHtml(html);
      }
    }
    _bindEvent() {
      super._bindEvent();
      if (typeof ChromeXt.cspRules == "undefined") {
        this._$el.find("li.eruda-csp-rules")[0].style = "display:none;";
      }
      this._$el
        .on("click", ".eruda-user-agent .eruda-icon-save", (e) => {
          this._container.notify("User-Agent config saved");
          e.stopPropagation();
          ChromeXt(
            JSON.stringify({
              action: "userAgent",
              payload: {
                origin: window.location.origin,
                data: this._$el.find("li.eruda-user-agent > div").text(),
              },
            })
          );
        })
        .on("click", ".eruda-user-agent .eruda-icon-reset", (_e) => {
          this._container.notify("User-Agent will be restored after refresh");
          ChromeXt(
            JSON.stringify({
              action: "userAgent",
              payload: { origin: window.location.origin },
            })
          );
        })
        .on("click", ".eruda-csp-rules .eruda-icon-save", (e) => {
          this._container.notify("CSP Rules config saved");
          e.stopPropagation();
          ChromeXt(
            JSON.stringify({
              action: "cspRule",
              payload: {
                origin: window.location.origin,
                data: this._$el.find("li.eruda-csp-rules > div").text(),
              },
            })
          );
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
        .on("change", "#new_script", (e) => {
          Array.from(e.curTarget.files).forEach((f) => {
            if (f.name.endsWith(".user.js")) {
              f.text().then((s) => {
                ChromeXt(
                  JSON.stringify({ action: "installScript", payload: s })
                );
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
  eruda._configured = true;
  eruda._localConfig = () => {
    if (!document.querySelector("#eruda")) {
      return;
    }
    addErudaStyle(
      "chromext_new_icons",
      ".eruda-icon-add:before { content: '➕'; font-size: 10px; vertical-align: 3px; } .eruda-icon-save:before { content: '💾'; font-size: 10px; vertical-align: 3px; }"
    );
    addErudaStyle(
      "chromext_eruda_dom_fix",
      "#eruda-elements div.eruda-dom-viewer-container { overflow-x: hidden;} #eruda-elements div.eruda-dom-viewer-container > div.eruda-dom-viewer { overflow-x: scroll;} .luna-dom-viewer { min-width: 80vw;}"
    );
    addErudaStyle(
      "chromext_plugin",
      "#eruda-info li .eruda-title span {padding: 4px 5px; margin: 0; float: right;} #eruda-info .eruda-user-agent h2, #eruda-info .eruda-csp-rules h2 { padding-bottom: 12px;} #eruda-info .eruda-userscripts div.eruda-content, #eruda-resources div.eruda-commands {display: flex; flex-wrap: wrap; justify-content: space-around; > span {padding: 0.3em; margin: 0.3em; border: 0.5px solid violet;}}"
    );
    document.addEventListener("securitypolicyviolation", (e) => {
      if (e.blockedURI == "data" && e.violatedDirective == "font-src") {
        retryEruda("Eruda icons are blocked in this page for known reason");
      } else if (
        e.blockedURI == "inline" &&
        e.target == document.querySelector("#eruda")
      ) {
        retryEruda("Eruda is blocked in this page for known reason");
      }
    });
  };

  function generateQuerySelector(el) {
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
      return generateQuerySelector(el.parentNode) + " > " + str;
    }
    return str;
  }

  function addErudaStyle(id, content) {
    if (!document.querySelector("#eruda")) {
      return;
    }
    const erudaroot = document.querySelector("#eruda").shadowRoot;
    if (erudaroot.querySelector("style#" + id)) {
      return;
    }
    const style = document.createElement("style");
    style.id = id;
    style.setAttribute("type", "text/css");
    style.textContent = content;
    erudaroot.append(style);
  }
} else if (typeof eruda == "undefined") {
  retryEruda("Eruda is blocked in this page for unknown reason");
}
