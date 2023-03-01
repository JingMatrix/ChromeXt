package org.matrix.chromext.script

import java.io.File
import java.io.FileReader
import org.matrix.chromext.Chrome
import org.matrix.chromext.utils.Log

const val GM_addStyle =
    """
function GM_addStyle(css) {
	const style = document.createElement("style");
	style.setAttribute("type", "text/css");
	style.textContent = css;

	const head = document.querySelector("head");
	if (!head) {
		if (document.readyState == "loading") {
			window.addEventListener("DOMContentLoaded", () => {
				(document.head || document.documentElement).appendChild(style);
			});
		} else {
			document.documentElement.appendChild(style);
		};
	} else {
		head.appendChild(style);
	}
}
"""

const val GM_addElement =
    """
function GM_addElement(parent_node, tag_name, attributes) {
	const element = document.createElement(tag_name);
	for (const [key, value] of Object.entries(attributes)) {
		if (key != "textContent") {
			element.setAttribute(key, value);
		} else {
			element.textContent = value;
		}
	}
	if (!parent_node) {
		if (document.readyState == "loading") {
			window.addEventListener("DOMContentLoaded", () => {
				(document.body || document.documentElement).appendChild(element);
			});
		} else {
			document.documentElement.appendChild(element);
		}
	} else {
		parent_node.appendChild(element);
	}
}

function GM_addElement(tag_name, attributes) {
	GM_addElement(document.head || document.body, tag_name, attributes);
}
"""

const val GM_xmlhttpRequest =
    """
function GM_xmlhttpRequest(/* object */ details) {
	details.method = details.method ? details.method.toUpperCase() : "GET";

	if (!details.url) {
		throw new Error("GM_xmlhttpRequest requires a URL.");
	}

	const xmlhr = new XMLHttpRequest();

	for (const [key, value] of Object.entries(details)) {
		if (key.startsWith("on")) {
			xmlhr.addEventListener(key.substring(2), value);
		}
	}
	if ("timeout" in details) {
		xmlhr.timeout = details.timeout;
	}

	details.user = details.use || null;
	details.password = details.password || null;
	details.synchronous = details.synchronous || false;

	xmlhr.open(details.method, details.url, !details.synchronous || true, details.user, details.password);

	if ("headers" in details) {
		for (const header in details.headers) {
			xmlhr.setRequestHeader(header, details.headers[header]);
		}
	}
	if ("data" in details) {
		if ("binary" in details) {
			const blob = new Blob([details.data.toString()], "text/plain");
			xmlhr.send(blob);
		} else {
			xmlhr.send(details.data);
		}
	} else {
		xmlhr.send();
	}

	return xmlhr;
}
"""

const val GM_openInTab =
    """
function GM_openInTab(url, options) {
	const gm_window = window.open(url, "_blank");
	if ("active" in options && options.active ) {
		gm_window.focus();
	}
	return gm_window;
}
"""

fun encodeScript(script: Script): String? {
  var code = script.code

  var backtrick = ""
  var dollarsign = ""
  var backslash = ""
  if (script.shouldWrap) {
    // Encode source code by simple replacement
    val alphabet: List<Char> = ('a'..'z') + ('A'..'Z')
    backtrick = List(16) { alphabet.random() }.joinToString("")
    dollarsign = List(16) { alphabet.random() }.joinToString("")
    backslash = List(16) { alphabet.random() }.joinToString("")
    code = code.replace("`", backtrick)
    code = code.replace("$", dollarsign)
    code = code.replace("\\", backslash)
    code = """Function(ChromeXt_decode(`${code}`))();"""
  }

  val imports =
      script.require
          .map {
            if (it != "") {
              """await import("${it}")"""
            } else {
              it
            }
          }
          .joinToString(separator = ";")
  if (imports != "") {
    code = """(async ()=>{${imports};${code}})();"""
  }

  when (script.runAt) {
    RunAt.START -> {}
    RunAt.END -> code = """window.addEventListener("DOMContentLoaded",()=>{${code}});"""
    RunAt.IDLE -> code = """window.addEventListener("load",()=>{${code}});"""
  }

  script.grant.forEach granting@{
    val function = it
    if (function == "") {
      return@granting
    }
    when (function) {
      "GM_addStyle" -> code = GM_addStyle + code
      "GM_addElement" -> code = GM_addElement + code
      "GM_openInTab" -> code = GM_openInTab + code
      "GM_xmlhttpRequest" -> code = GM_xmlhttpRequest + code
      "GM_info" ->
          code =
              "const GM_info = {script:{name:'${script.id.split(":").first()}',namespace:'${script.id.split(":").last()}'}};" +
                  code
      "unsafeWindow" -> code = "const unsafeWindow = window;" + code
      "GM_log" -> code = "const GM_log = console.log.bind(console);" + code
      "GM_deleteValue" ->
          code = "const GM_deleteValue = localStorage.removeItem.bind(localStorage);" + code
      "GM_setValue" -> code = "const GM_setValue = localStorage.setItem.bind(localStorage);" + code
      "GM_getValue" -> code = "const GM_getValue = localStorage.getItem.bind(localStorage);" + code
      "GM_getResourceURL" -> {
        var GM_ResourceURL = "GM_ResourceURL={"
        script.resource.forEach {
          val content = it.split(" ")
          val name = content.first()
          if (name == "") return@granting
          val url = content.last()
          GM_ResourceURL += name + ":'" + url + "',"
        }
        GM_ResourceURL += "};"
        code = GM_ResourceURL + "const GM_getResourceURL = (name) => GM_ResourceURL[name];" + code
      }
      "GM_getResourceText" -> {
        var GM_ResourceText = "GM_ResourceText={"
        runCatching {
              script.resource.forEach {
                val name = it.split(" ").first()
                if (name == "") return@granting
                val file =
                    File(
                        Chrome.getContext().getExternalFilesDir(null),
                        resourcePath(script.id, name))
                if (file.exists()) {
                  val text = FileReader(file).use { it.readText() }
                  GM_ResourceText +=
                      name +
                          ":'" +
                          text
                              .replace("\n", "ChromeXt_ResourceText_NEWLINE")
                              .replace("'", "ChromeXt_ResourceText_QUOTE") +
                          "',"
                }
              }
            }
            .onFailure { Log.ex(it) }
        GM_ResourceText += "};"
        code =
            GM_ResourceText +
                """const GM_getResourceText = (name) => (name in GM_ResourceText) ? GM_ResourceText[name].replaceAll("ChromeXt_ResourceText_NEWLINE", "\n").replaceAll("ChromeXt_ResourceText_QUOTE", "'") : "ChromeXt failed to get resource";""" +
                code
      }
      "GM_listValues" ->
          code =
              "const GM_listValues = ()=> [...Array(localStorage.length).keys()].map(x=>localStorage.key(x));" +
                  code
      else ->
          code =
              """function ${function}(...args) {console.error("${function} is not implemented in ChromeXt yet, called with", args)}""" +
                  code
    }
  }

  if (script.shouldWrap) {
    // Add decode function, and it finally contains only three backtricks in total
    code =
        """function ChromeXt_decode(src) {return src.replaceAll("${backtrick}", "`").replaceAll("${dollarsign}", "$").replaceAll("${backslash}", "\\");}""" +
            code
  }
  return code
}

const val erudaToggle =
    """
if (typeof globalThis.eruda != "undefined") {
	if (eruda._isInit) {
		eruda.hide();
		eruda.destroy();
	} else {
		eruda.init();
		eruda.show();
	}
}
"""
