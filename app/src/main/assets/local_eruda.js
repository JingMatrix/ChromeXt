if (
  typeof globalThis.eruda != "undefined" &&
  typeof eruda.configured == "undefined"
) {
  class Filter {
    #filter = [];
    constructor() {}
    refresh() {
      let filter = localStorage.getItem("ChromeXt_filter");
      filter = JSON.parse(filter);
      if (Array.isArray(filter)) {
        this.#filter = filter;
      } else {
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
        localStorage.setItem("ChromeXt_filter", JSON.stringify(this.#filter));
      } else {
        localStorage.removeItem("ChromeXt_filter");
      }
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
    init($el, container) {
      super.init($el, container);
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
  eruda.configured = true;
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
