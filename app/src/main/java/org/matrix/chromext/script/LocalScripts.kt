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
	try {
		(document.head || document.documentElement).appendChild(style);
	} catch {
		setTimeout(() => {document.head.appendChild(style);}, 0);
	}
}
"""

const val GM_addElement =
    """
function GM_addElement() {
	// parent_node, tag_name, attributes
	if (arguments.length == 2) {
		arguments = [document.head, arguments[0], arguments[1]];
	};
	if (arguments.length != 3) { return };
	const element = document.createElement(arguments[1]);
	for (const [key, value] of Object.entries(arguments[2])) {
		if (key != "textContent") {
			element.setAttribute(key, value);
		} else {
			element.textContent = value;
		}
	}
	try {
		arguments[0].appendChild(element);
	} catch {
		setTimeout(() => {document.head.appendChild(element);}, 0);
	}
}
"""

const val GM_xmlhttpRequest =
    """
function GM_xmlhttpRequest(details) {
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

const val GM_registerMenuCommand =
    """
function GM_registerMenuCommand(title, listener, accessKey="Dummy") {
	if (typeof ChromeXt.MenuCommand == "undefined") {
		ChromeXt.MenuCommand = [];
	}
	ChromeXt.MenuCommand.push({title, listener});
	return ChromeXt.MenuCommand.length - 1;
}
"""

const val GM_addValueChangeListener =
    """
function GM_addValueChangeListener(key, listener) {
	if (typeof ChromeXt.ValueChangeListener == "undefined") {
		ChromeXt.ValueChangeListener = [];
	}
	ChromeXt.ValueChangeListener.push({key, listener});
	return ChromeXt.ValueChangeListener.length - 1;
}
"""

const val GM_setValue =
    """
function GM_setValue(key, value) {
	if (typeof ChromeXt.ValueChangeListener != undefined) {
		const old_value = localStorage.getItem(key + '_ChromeXt_Value');
		if (old_value != null) {
			ChromeXt.ValueChangeListener.forEach(e => {if (e.key == key) {e.listener(JSON.parse(old_value), value, false)}});
		}
	}
	localStorage.setItem(key + '_ChromeXt_Value', JSON.stringify(value));
}
"""

const val GM_bootstrap =
    """
function GM_bootstrap() {
	const row = /\/\/\s+@(\S+)\s+(.+)/g;
	const meta = GM_info.script;
	let match;
	while ((match = row.exec(GM_info.scriptMetaStr)) !== null) {
		if (meta[match[1]]) {
			if (typeof meta[match[1]] == "string") meta[match[1]] = [meta[match[1]]];
			meta[match[1]].push(match[2]);
		} else meta[match[1]] = match[2];
	}
}
"""

fun encodeScript(script: Script): String? {
  var code = script.code

  if (!script.meta.startsWith("// ==UserScript==")) {
    code = script.meta + code
  }

  if (script.require.size > 0) {
    code =
        GM_addElement +
            "(async ()=> {" +
            script.require
                .map {
                  "try{await import('${it}')}catch{GM_addElement('script',{textContent: await (await fetch('${it}')).text()})};"
                }
                .joinToString("") +
            code +
            "})();"
  }

  when (script.runAt) {
    RunAt.START -> {}
    RunAt.END -> code = "window.addEventListener('DOMContentLoaded',()=>{${code}});"
    RunAt.IDLE -> code = "window.addEventListener('load',()=>{${code}});"
  }

  code =
      GM_bootstrap +
          "const GM_info = {scriptMetaStr:`${script.meta}`,script:{antifeatures:{},options:{override:{}}}};GM_bootstrap();" +
          code

  script.grant.forEach granting@{
    val function = it
    when (function) {
      "GM_addStyle" -> code = GM_addStyle + code
      "GM_addElement" -> if (script.require.size == 0) code = GM_addElement + code
      "GM_openInTab" -> code = GM_openInTab + code
      "GM_info" -> return@granting
      "GM_xmlhttpRequest" -> code = GM_xmlhttpRequest + code
      "unsafeWindow" -> code = "const unsafeWindow = window;" + code
      "GM_log" -> code = "const GM_log = console.log.bind(console);" + code
      "GM_deleteValue" ->
          code =
              "function GM_deleteValue(key) {localStorage.removeItem(key + '_ChromeXt_Value')};" +
                  code
      "GM_setValue" -> code = GM_setValue + code
      "GM_getValue" ->
          code =
              "function GM_getValue(key, default_value) {let value = localStorage.getItem(key + '_ChromeXt_Value') || default_value;try {value = JSON.parse(value)} finally {return value}};" +
                  code
      "GM_addValueChangeListener" -> code = GM_addValueChangeListener + code
      "GM_removeValueChangeListener" ->
          code =
              "function GM_removeValueChangeListener(index) {ChromeXt.ValueChangeListener.splice(index, 1)};" +
                  code
      "GM_registerMenuCommand" -> code = GM_registerMenuCommand + code
      "GM_unregisterMenuCommand" ->
          code =
              "function GM_unregisterMenuCommand(index) {ChromeXt.MenuCommand.splice(index, 1)};" +
                  code
      "GM_getResourceURL" -> {
        var GM_ResourceURL = "GM_ResourceURL={"
        script.resource.forEach {
          val content = it.split(" ")
          if (content.size != 2) return@granting
          val name = content.first()
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
              "function ${function}(...args) {console.error('${function} is not implemented in ChromeXt yet, called with', args)}" +
                  code
    }
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
		eruda._localConfig();
		eruda.show();
	}
}
"""
