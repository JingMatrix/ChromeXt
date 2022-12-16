package org.matrix.chromext.script

const val promptInstallUserScript: String =
    """
setTimeout(() => {
	let old_script = "";
	function installScript() {
		let script = document.querySelector("body > pre").innerText;
		if (script != old_script) {
			old_script = script;
			let install = confirm("Allow ChromeXt to install / update this userscript?");
			if (install) { 
				console.debug(JSON.stringify({"action": "installScript", "payload": script}));
			}
		}
	};
	document.querySelector("body > pre").setAttribute("contenteditable", true);
	installScript();
	let asked = false;
	addEventListener("contextmenu", (e) => {
	    addEventListener("click", (_event) => {
			if (!asked) {
				event.preventDefault();
				setTimeout(()=>{asked=false;installScript();}, 100);
				asked = true;
			}
		}, {once: true});
	});
}, 100)
"""

const val GM_addStyle =
    """
function GM_addStyle(/* String */ styles) {
	const oStyle = document.createElement("style");
	oStyle.setAttribute("type", "text/css");
	oStyle.appendChild(document.createTextNode(styles));

	const head = document.getElementsByTagName("head")[0];
	if (head === undefined) {
		// no head yet, stick it wherever
		document.documentElement.appendChild(oStyle);
	} else {
		head.appendChild(oStyle);
	}
};
"""

const val GM_addElement =
    """
function GM_addElement(parent_node, tag_name, attributes) {
	const element = document.createElement(tag_name);
	for (const [key, value] of Object.entries(attributes)) {
		element.setAttribute(key, value);
	};
	if (parent_node === undefined) {
		document.documentElement.appendChild(element);
	} else {
		parent_node.appendChild(element);
	};
};

function GM_addElement(tag_name, attributes) {
	GM_addElement(document.head || document.body, tag_name, attributes);
};
"""

fun encodeScript(script: Script): String? {
  var code = script.code
  if (script.encoded) {
    return null
  }

  val shouldWrap: Boolean = code.contains("\\`")

  var backtrick = ""
  if (shouldWrap) {
    // Encode source code by simple replacement
    val alphabet: List<Char> = ('a'..'z') + ('A'..'Z')
    backtrick = List(16) { alphabet.random() }.joinToString("")
    code = code.replace("`", backtrick)
    code = """Function(ChromeXt_decode(`${code}`))();"""
  }

  when (script.runAt) {
    RunAt.START -> {}
    RunAt.END -> code = """document.addEventListener("DOMContenLoaded",(_e)=>{${code})};"""
    RunAt.IDLE -> code = """window.onload=(_e)=>{setTimeout(()=>{${code}},10)};"""
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

  script.grant.forEach {
    val function = it
    when (function) {
      "GM_addStyle" -> code = GM_addStyle + code
      "GM_addElement" -> code = GM_addElement + code
      else ->
          code =
              """function ${function}(...args) {console.error("${function} is not implemented in ChromeXt yet, called with", args)};""" +
                  code
    }
  }

  if (shouldWrap) {
    // Add decode function, and it finally contains only three backtricks in total
    code = """function ChromeXt_decode(src) {return src.replaceAll("${backtrick}", "`");};""" + code
  }
  return code
}
