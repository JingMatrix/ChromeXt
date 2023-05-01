if (
  typeof globalThis.eruda != "undefined" &&
  typeof eruda._configured == "undefined"
) {
  const meta = document.createElement("meta");
  meta.setAttribute("name", "viewport");
  meta.setAttribute("content", "width=device-width, initial-scale=1");
  document.head.prepend(meta);

  class Filter {
    #filter = [];
    constructor() {}
    updateCosmeticFilter() {
      let filter = "";
      if (this.#filter.length > 0) {
        filter = "" + JSON.stringify(this.#filter);
      }
      globalThis.ChromeXt(
        JSON.stringify({
          action: "cosmeticFilter",
          payload: window.location.origin + filter,
        })
      );
    }
    refresh() {
      let filter = localStorage.getItem("ChromeXt_filter");
      try {
        filter = JSON.parse(filter);
        if (Array.isArray(filter)) {
          this.#filter = filter;
          this.updateCosmeticFilter();
        }
      } catch {
        localStorage.removeItem("ChromeXt_filter");
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
      this.#write();
    }
    new() {
      this.#filter.push("");
      this.#write();
    }
    #write() {
      if (this.#filter.length > 0) {
        let filter = JSON.stringify(this.#filter);
        localStorage.setItem("ChromeXt_filter", filter);
      } else {
        localStorage.removeItem("ChromeXt_filter");
      }
      this.updateCosmeticFilter();
    }
    save() {
      this.#filter = [];
      document
        .querySelector("#eruda")
        .shadowRoot.querySelectorAll(".eruda-filter-item")
        .forEach((it) => this.#filter.push(it.innerText.trim()));
      this.remove("");
    }
  }

  eruda._filter = new Filter();
  class elements extends eruda.Elements {
    constructor() {
      super();
      eruda._filter.refresh();
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
          self.refreshChromeXtFilter();
        })
        .on("click", ".eruda-add-filter", () => {
          eruda._filter.new();
          self.refreshChromeXtFilter();
        })
        .on("click", ".eruda-save-filter", () => {
          eruda._filter.save();
          self.refreshChromeXtFilter();
          container.notify("Filter Saved");
        })
        .on("click", ".script-command", (e) => {
          const index = e.curTarget.dataset.index;
          ChromeXt.MenuCommand[index].listener(e);
          eruda.hide();
        });
    }
    _initTpl() {
      super._initTpl();
      if (typeof ChromeXt.MenuCommand != "undefined") {
        this._$el.prepend(
          "<div class='eruda-section eruda-ChromeXt-MenuCommand'></div>"
        );
        this._$ChromeXtMenuCommand = this._$el.find(
          ".eruda-ChromeXt-MenuCommand"
        );
      }
      this._$el.prepend(
        "<div class='eruda-section eruda-ChromeXt-filter'></div>"
      );
      this._$ChromeXtFilter = this._$el.find(".eruda-ChromeXt-filter");
    }
    refresh() {
      return super.refresh().refreshChromeXtFilter().refreshMenuCommand();
    }
    refreshMenuCommand() {
      if (
        typeof ChromeXt.MenuCommand != "undefined" &&
        ChromeXt.MenuCommand.length > 0
      ) {
        const commands = ChromeXt.MenuCommand.map(
          (command, index) =>
            `<span data-index=${index} style="padding: 0.3em; margin: 0.3em; border: 0.5px solid violet;" class="script-command">${command.title}</span>`
        ).join("");
        this._$ChromeXtMenuCommand.html(`<h2 class="${c(
          "title"
        )}">UserScript Commands</h2>
		<div style="display: flex; flex-wrap: wrap; justify-content: space-around;">${commands}</div>
      `);
      }
    }
    refreshChromeXtFilter() {
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

      this._$ChromeXtFilter.html(`<h2 class="${c(
        "title"
      )}">ChromeXt Cosmetic Filters
			<div class="${c(
        "btn save-filter"
      )}" style="font-size:12px;"><span>💾</span></div>
			<div class="${c(
        "btn add-filter"
      )}" style="font-size:12px;"><span>➕</span></div>
		</h2>
		<ul>${filterHtml}</ul>
      `);

      this.refreshLocalStorage();
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
          `<ul><li class="chromext-user-agent"><h2 class="eruda-title">User Agent<span class="eruda-icon-save"></span><span class="eruda-icon-reset"></span></h2><div class="eruda-content" contenteditable="true">${navigator.userAgent}</div></li>` +
            html.substring(4)
        );
      } else {
        super._renderHtml(html);
      }
    }
    _bindEvent() {
      this._$el.on(
        "click",
        "li.chromext-user-agent > h2 > span.eruda-icon-save",
        (e) => {
          this._container.notify("User-Agent config saved");
          e.stopPropagation();
          globalThis.ChromeXt(
            JSON.stringify({
              action: "userAgent",
              payload:
                window.location.origin +
                "" +
                this._$el.find("li.chromext-user-agent > div").text(),
            })
          );
        }
      );

      this._$el.on(
        "click",
        "li.chromext-user-agent > h2 > span.eruda-icon-reset",
        (_e) => {
          this._container.notify("User-Agent will be restored after refresh");
          globalThis.ChromeXt(
            JSON.stringify({
              action: "userAgent",
              payload: window.location.origin,
            })
          );
        }
      );
      super._bindEvent();
    }
  }

  eruda.Elements = elements;
  eruda.Resources = resources;
  eruda.Info = info;
  eruda._configured = true;
  eruda._font_fix = `
	[class^='eruda-icon-']:before {
		font-size: 14px;
	}
	.eruda-icon-arrow-left:before {
	  content: '←';
	}
	.eruda-icon-arrow-right:before {
	  content: '→';
	}
	.eruda-icon-clear:before {
	  content: '✖';
	  font-size: 17px;
	}
	.eruda-icon-compress:before {
	  content: '🗎';
	}
	.eruda-icon-copy:before,
	.luna-text-viewer-icon-copy:before {
	  content: '⎘ ';
	  font-size: 16px;
	  font-weight: bold;
	}
	.eruda-icon-delete:before {
	  content: '⌫';
	  font-weight: bold;
	}
	.eruda-icon-expand:before {
	  content: '⌄';
	}
	.eruda-icon-eye:before {
	  content: '🧿';
	}
	div.eruda-btn.eruda-search {
	  margin-top: 4px;
	}
	.eruda-icon-filter:before {
	  content: '⭃';
      font-size: 19px;
      font-weight: bold;
      margin-right: -5px;
      display: block;
      transform: rotate(90deg);
	}
	.eruda-icon-play:before {
	  content: '▷';
	}
	.eruda-icon-record:before {
	  content: '●';
	}
	.eruda-icon-refresh:before {
	  content: '↻';
	  font-size: 18px;
	  font-weight: bold;
	}
	.eruda-icon-reset:before {
	  content: '↺';
	  font-size: 18px;
	  font-weight: bold;
      display: block;
	  transform: rotate(270deg) translate(5px, 0);
	}
	.eruda-icon-search:before {
	  content: '🔍';
	}
	.eruda-icon-select:before {
	  content: '➤';
	  font-size: 14px;
	  display: block;
	  transform: rotate(232deg);
	}
	.eruda-icon-tool:before {
	  content: '⚙';
	  font-size: 30px;
	}
	.luna-console-icon-error:before {
	  content: '✗';
	}
	.luna-console-icon-warn:before {
	  content: '⚠';
	}
	[class\$='icon-caret-right']:before,
	[class\$='icon-arrow-right']:before {
	  content: '▼';
	  font-size: 9px;
	  display: block;
	  transform: rotate(-0.25turn);
	}
	[class\$='icon-caret-down']:before,
	[class\$='icon-arrow-down']:before {
	  content: '▼';
	  font-size: 9px;
	}
	`;
  eruda._localConfig = () => {
    if (!document.querySelector("#eruda")) {
      return;
    }
    addErudaStyle(
      "chromext_eruda_dom_fix",
      "#eruda-elements div.eruda-dom-viewer-container { overflow-x: hidden;} #eruda-elements div.eruda-dom-viewer-container > div.eruda-dom-viewer { overflow-x: scroll;} .luna-dom-viewer { min-width: 80vw;}"
    );
    addErudaStyle(
      "chromext_plugin",
      "li.chromext-user-agent .eruda-icon-save:before { content: '💾'; font-size: 10px; vertical-align: 3px;} li.chromext-user-agent span {padding: 4px 5px; margin: 0; float: right;} #eruda-info li.chromext-user-agent h2.eruda-title {padding-bottom: 16px;}"
    );
    if (typeof eruda._shouldFixfont == "undefined") {
      eruda._shouldFixfont = false;
      document.addEventListener("securitypolicyviolation", (e) => {
        if (e.blockedURI == "data" && e.violatedDirective == "font-src") {
          eruda._shouldFixfont = true;
          addErudaStyle("chromext_eruda_font_fix", eruda._font_fix);
        }
      });
    } else if (eruda._shouldFixfont) {
      addErudaStyle("chromext_eruda_font_fix", eruda._font_fix);
    }
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
} else if (typeof globalThis.eruda == "undefined") {
  const cspRule = "script-src 'none'";
  const meta = document.head.querySelector(`meta[content="${cspRule}"]`);
  if (meta && meta.getAttribute("http-equiv") == "Content-Security-Policy") {
    alert(
      "Content-Security-Policy is set, but you need to fully restart the browser to clean cached third-party JavaScripts."
    );
  } else if (
    confirm(
      "Eruda is blocked, it is advisable to use the official 'Content-Security-Policy Blocker' UserScript to block JavaScripts on this website.\n\nDo you want to proceed in this way (current page will be reloaded)?"
    )
  ) {
    localStorage.setItem("CSPBlocker", cspRule);
    globalThis.ChromeXt(
      JSON.stringify({
        action: "installDefault",
        payload: "CSP.user.js",
      })
    );
    window.location.reload();
  }
}
