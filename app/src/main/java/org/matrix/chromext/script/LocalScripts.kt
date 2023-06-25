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
	if (!details.url) {
		throw new Error("GM_xmlhttpRequest requires a URL.");
	}
	const uuid = Math.random();
	details.method = details.method ? details.method.toUpperCase() : "GET";
	ChromeXt(JSON.stringify({action: 'xmlhttpRequest', payload: {id: GM_info.script.id, request: details, uuid}}));

	function base64ToBytes(base64) {
	  const binString = atob(base64);
	  return Uint8Array.from(binString, (m) => m.codePointAt(0));
	}

	function base64ToBlob(base64, type) {
	  return new Blob([base64ToBytes(base64).buffer], {type});
	}

	function base64ToUTF8(base64) {
	  return new TextDecoder().decode(base64ToBytes(base64));
	}

	window.addEventListener('xmlhttpRequest', (e) => {
		if (e.detail.id == GM_info.script.id && e.detail.uuid == uuid) {
			let data = e.detail.data;
			switch (e.detail.type) {
				case 'load':
					data.readyState = 4;
					data.finalUrl = data.responseHeaders.Location || details.url;
					if ('overrideMimeType' in details) data.responseHeaders['Content-Type'] = details.overrideMimeType;
					if ('responseType' in details) {
						const base64 = data.responseText;
						const type = data.responseHeaders['Content-Type'] || '';
						switch (details.responseType) {
							case 'arraybuffer': data.response = base64ToBytes(base64).buffer; break;
							case 'blob': data.response = base64ToBlob(base64, type); break;
							case 'stream': data.response = base64ToBlob(base64, type).stream(); break;
							case 'json': data.response = JSON.parse(base64ToUTF8(base64)); break;
							defualt: data.response = atob(base64);
						}
					} else {
						data.responseText = base64ToUTF8(data.responseText);
						data.response = data.responseText;
					}
					details.onload(data); break;
				case 'error':
					details.onerror(data); break;
				case 'abort':
					details.onabort(data); break;
				case 'timeout':
					details.ontimeout(data); break;
				defualt: console.log(e.detail);
			}
		}
	})
	return {abort: () => {ChromeXt(JSON.stringify({action: 'abortRequest', payload: uuid}));}}
}
"""

const val GM_download =
    """
function GM_download(details) {
	if (arguments.length == 2) {
		details = {url: arguments[0], name: arguments[1]}
	}
	return GM_xmlhttpRequest({
		...details, responseType: 'blob',
		onload: (res) => {
			if (res.status !== 200) return console.error('Error loading: ', details.url, res);
			const link = document.createElement('a');
			link.href = URL.createObjectURL(res.response);
			link.download = details.name || details.url.split('#').shift().split('?').shift().split('/').pop();
			link.dispatchEvent(new MouseEvent('click'));
			setTimeout(URL.revokeObjectURL(link.href), 1000);
		}
	});
}
"""

const val GM_openInTab =
    """
function GM_openInTab(url, options) {
	const gm_window = window.open(url, "_blank");
	if (typeof options == "boolean") {
		options = {active: !options};
	}
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
	const index = ChromeXt.MenuCommand.findIndex(
		(e) => e.id == GM_info.script.id && e.title == title
	);
	if (index != -1) {
		ChromeXt.MenuCommand[index].listener = listener;
		return index;
	}
	ChromeXt.MenuCommand.push({id: GM_info.script.id, title, listener, enabled: true});
	return ChromeXt.MenuCommand.length - 1;
}
"""

const val GM_addValueChangeListener =
    """
function GM_addValueChangeListener(key, listener) {
	const index = valueChangeListener.findIndex(
		(e) => e.id == GM_info.script.id && e.key == key 
	);
	if (index != -1) {
		valueChangeListener[index].listener = listener;
		return index;
	}
	valueChangeListener.push({id: GM_info.script.id, key, listener, enabled: true});
	return valueChangeListener.length - 1;
}
"""

const val GM_setValue =
    """
function GM_setValue(key, value) {
	if (key in scriptStorage && JSON.stringify(scriptStorage[key]) == JSON.stringify(value)) return;
	scriptStorage[key] = value;
	ChromeXt(JSON.stringify({action: 'scriptStorage', payload: {id: GM_info.script.id, data: scriptStorage, uuid}}));
}
"""

const val GM_deleteValue =
    """
function GM_deleteValue(key, value) {
	if (key in scriptStorage) {
		delete scriptStorage[key];
		ChromeXt(JSON.stringify({action: 'scriptStorage', payload: {id: GM_info.script.id, data: scriptStorage, uuid}}));
	}
}
"""

const val GM_getValue =
    """
function GM_getValue(key, default_value) {
	return key in scriptStorage ? scriptStorage[key] : default_value;
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
	meta.includes = meta.include;
	meta.matches = meta.match;
	meta.excludes = meta.exclude;
	delete meta.include;
	delete meta.match;
	delete meta.exclude;
}
"""

const val GM_globalThis =
    """
const globalThis = new Proxy(window, {
	get(target, prop) {
		if (prop.startsWith('_')) {
			return undefined; 
		} else {
			let value = target[prop]; return (typeof value === 'function') ? value.bind(target) : value; 
		}
	}
});
"""

fun encodeScript(script: Script): String? {
  var code = script.code

  if (!script.meta.startsWith("// ==UserScript==")) {
    code = script.meta + code
  }

  if (script.grant.contains("GM_setValue") ||
      script.grant.contains("GM_getValue") ||
      script.grant.contains("GM_listValues")) {
    code =
        """
	window.addEventListener('scriptStorage', (e) => {
		if (e.detail.id == GM_info.script.id) {
			for (const [key, value] of Object.entries(e.detail.data)) {
				if (key in scriptStorage && JSON.stringify(scriptStorage[key]) == JSON.stringify(value)) {
					continue;
				}
				valueChangeListener.forEach(e => {if (e.id == GM_info.script.id && e.key == key) {
					e.listener(scriptStorage[key], value, e.detail.uuid != uuid)
				}});
			}
			scriptStorage = e.detail.data;
		}
	});
	{ ${code} };
	"""
    if (script.storage == "") {
      script.storage = "{}"
    }
    code =
        "const valueChangeListener = []; let scriptStorage = ${script.storage}; const uuid = Math.random();" +
            code
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

  code =
      GM_bootstrap +
          "const GM_info = {scriptMetaStr:`${script.meta}`,script:{antifeatures:{},options:{override:{}}}};GM_bootstrap();GM_info.script.id=`${script.id}`;" +
          code

  script.grant.forEach granting@{
    val function = it
    when (function) {
      "GM_addStyle" -> code = GM_addStyle + code
      "GM_addElement" -> if (script.require.size == 0) code = GM_addElement + code
      "GM_openInTab" -> code = GM_openInTab + code
      "GM_info" -> return@granting
      "GM_download" -> code = GM_xmlhttpRequest + GM_download + code
      "GM_xmlhttpRequest" ->
          if (!script.grant.contains("GM_download")) code = GM_xmlhttpRequest + code
      "unsafeWindow" -> code = "const unsafeWindow = window;" + code
      "GM_log" -> code = "const GM_log = console.log.bind(console);" + code
      "GM_setValue" -> code = GM_setValue + code
      "GM_deleteValue" -> code = GM_deleteValue + code
      "GM_getValue" -> code = GM_getValue + code
      "GM_addValueChangeListener" -> code = GM_addValueChangeListener + code
      "GM_removeValueChangeListener" ->
          code =
              "function GM_removeValueChangeListener(index) {valueChangeListener[index].enabled = false;};" +
                  code
      "GM_registerMenuCommand" -> code = GM_registerMenuCommand + code
      "GM_unregisterMenuCommand" ->
          code =
              "function GM_unregisterMenuCommand(index) {ChromeXt.MenuCommand[index].enabled = false;};" +
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
      "GM_listValues" -> code = "const GM_listValues = ()=> Object.keys(scriptStorage);" + code
      else ->
          if (!function.startsWith("GM.")) {
            code =
                "function ${function}(...args) {console.error('${function} is not implemented in ChromeXt yet, called with', args)}" +
                    code
          } else {
            val name = function.substring(3)
            if (script.grant.contains("GM_${name}")) {
              code =
                  "GM.${name} = async (...arguments) => new Promise((resolve, reject) => {resolve(GM_${name}(...arguments))});" +
                      code
            }
          }
    }
  }

  if (!script.grant.contains("unsafeWindow")) {
    code = GM_globalThis + code
  }

  code = "const ChromeXt_scriptLoader = () => {const GM = {};${code}};"
  when (script.runAt) {
    RunAt.START -> code = "(()=>{${code} ChromeXt_scriptLoader()})();"
    RunAt.END ->
        code =
            "(()=>{${code} if (document.readyState != 'loading') {ChromeXt_scriptLoader()} else {window.addEventListener('DOMContentLoaded', ChromeXt_scriptLoader)}})();"
    RunAt.IDLE ->
        code =
            "(()=>{${code} if (document.readyState == 'complete') {ChromeXt_scriptLoader()} else {window.addEventListener('load', ChromeXt_scriptLoader)}})();"
  }

  return code
}

const val openEruda =
    "try{ if (eruda._isInit) { eruda.hide(); eruda.destroy(); } else { eruda.init(); eruda._localConfig(); eruda.show(); } } catch (e) { globalThis.ChromeXt(JSON.stringify({ action: 'loadEruda', payload: ''})) }"
