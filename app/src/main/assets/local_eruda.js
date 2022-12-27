if (
  typeof globalThis.eruda != "undefined" &&
  typeof eruda.configured == "undefined"
) {
  class elements extends eruda.Elements {
    constructor() {
      super();
      this._deleteNode = () => {
        const node = this._curNode;
        const selector = generateQuerySelector(node);
        let filter = localStorage.getItem("ChromeXt_filter");
        if (filter == null) {
          filter = [selector];
        } else {
          filter = JSON.parse(filter);
          if (!filter.includes(selector)) {
            filter.push(selector);
          }
        }
        localStorage.setItem("ChromeXt_filter", JSON.stringify(filter));

        if (node.parentNode) {
          node.parentNode.removeChild(node);
        }
      };
    }
  }

  class resources extends eruda.Resources {
    _bindEvent() {
      super._bindEvent();
      const self = this;
      const $el = this._$el;
      const container = this._container;
      $el
        .on("click", ".eruda-delete-filter", function (e) {
          const key = e.curTarget.dataset.key;
          let filter = JSON.parse(localStorage.getItem("ChromeXt_filter"));
          filter = filter.filter((item) => item !== key);
          if (filter.length > 0) {
            localStorage.setItem("ChromeXt_filter", JSON.stringify(filter));
          } else {
            localStorage.removeItem("ChromeXt_filter");
          }
          self.refreshLocalStorage()._render();
        })
        .on("click", ".eruda-add-filter", () => {
          let filter = localStorage.getItem("ChromeXt_filter");
          if (filter != null) {
            filter = JSON.parse(filter);
            filter.push("");
            localStorage.setItem("ChromeXt_filter", JSON.stringify(filter));
          } else {
            localStorage.setItem("ChromeXt_filter", '[""]');
          }
          self.refreshLocalStorage()._render();
        })
        .on("click", ".eruda-save-filter", () => {
          container.notify("Filter Saved");
          let filter = [];
          document
            .querySelector("#eruda")
            .shadowRoot.querySelectorAll(".eruda-filter-item")
            .forEach((it) => {
              const rule = it.innerText.trim();
              if (rule != "") {
                filter.push(rule);
              }
            });
          if (filter.length > 0) {
            localStorage.setItem("ChromeXt_filter", JSON.stringify(filter));
          }
          self.refreshLocalStorage()._render();
        });
    }
    _renderHtml(html) {
      const c = (str) => {
        return str
          .split(" ")
          .map((it) => "eruda-" + it)
          .join(" ");
      };
      let filterHtml = "<tr><td>Empty</td></tr>";

      let filter = localStorage.getItem("ChromeXt_filter");
      if (filter != null) {
        filter = JSON.parse(filter);
        if (Array.isArray(filter)) {
          filterHtml = filter
            .map(
              (key) => `<tr>
          <td contentEditable="true" class="${c("filter-item")}">${key}</td>
          <td class="${c("control")}">
            <span class="${c(
              "icon-delete delete-filter"
            )}" data-key="${key}" data-type="local"></span>
          </td>
        </tr>`
            )
            .join("");
        } else {
          localStorage.removeItem("ChromeXt_filter");
        }
      }

      const ChromeXtFilterHtml = `<div class="${c("section ChromeXt-filter")}">
		<h2 class="${c("title")}">ChromeXt Cosmetic Filters
			<div class="${c(
        "btn save-filter"
      )}" style="padding-left: 5px"><span>💾</span></div>
			<div class="${c(
        "btn add-filter"
      )}" style="padding-right: 5px"><span>➕</span></div>
		</h2>
		<div class="${c("content")}">
        <table>
          <tbody>
            ${filterHtml}
          </tbody>
        </table>
      </div>
    </div>`;

      super._renderHtml(ChromeXtFilterHtml + html);
    }
  }

  eruda.Elements = elements;
  eruda.Resources = resources;
  eruda.configured = true;
}

function generateQuerySelector(el) {
  if (el.tagName.toLowerCase() == "html") return "html";
  let str = el.tagName.toLowerCase();
  str += el.id != "" ? "#" + el.id : "";
  if (el.className != "") {
    let classes = el.className.split(/\s/);
    str += "." + classes.join(".");
  }
  if (str == el.tagName.toLowerCase()) {
    return generateQuerySelector(el.parentNode) + " > " + str;
  } else {
    return str;
  }
}
