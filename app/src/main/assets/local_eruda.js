if (
  typeof globalThis.eruda != "undefined" &&
  typeof eruda._configured == "undefined"
) {
  class Filter {
    #filter = [];
    constructor() {}
    updateCosmeticFilter() {
      let filter = "";
      if (this.#filter.length > 0) {
        filter = ";" + JSON.stringify(this.#filter);
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
        });
    }
    _initTpl() {
      super._initTpl();
      this._$el.prepend(
        "<div class='eruda-section eruda-ChromeXt-filter'></div>"
      );
      this._$ChromeXtFilter = this._$el.find(".eruda-ChromeXt-filter");
    }
    refresh() {
      return super.refresh().refreshChromeXtFilter();
    }
    refreshChromeXtFilter() {
      const c = (str) => {
        return str
          .split(" ")
          .map((it) => "eruda-" + it)
          .join(" ");
      };
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

  eruda.Elements = elements;
  eruda.Resources = resources;
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
    document.addEventListener("securitypolicyviolation", (e) => {
      if (e.blockedURI == "data" && e.violatedDirective == "font-src") {
        addErudaStyle("chromext_eruda_font_fix", eruda._font_fix);
      }
    });
  };
}

function generateQuerySelector(el) {
  if (el.tagName.toLowerCase() == "html") return "html";
  let str = el.tagName.toLowerCase();
  if (el.id != "") {
    str += "#" + el.id;
  } else if (el.className != "") {
    let classes = el.className.split(/\s/).filter((rule) => rule.trim() != "");
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
