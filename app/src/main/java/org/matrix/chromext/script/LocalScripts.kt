package org.matrix.chromext.script

const val promptInstallUserScript: String =
    """
setTimeout(() => {
	let install = confirm("Allow ChromeXt to intall / update this userscript?");
	if (install) {
		let script = document.querySelector("body > pre").innerHTML.replaceAll("&amp;", "&").replaceAll("&lt;", "<").replaceAll("&gt;", ">").replaceAll("&quot;", "\"");
		console.debug(JSON.stringify({"action": "installScript", "payload": script}));
	}
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

fun encodeScript(script: Script): String? {
  var code = script.code
  if (script.encoded) {
    return null
  }

  // Encode source code by simple replacement
  val alphabet: List<Char> = ('a'..'z') + ('A'..'Z')
  val backtrick: String = List(16) { alphabet.random() }.joinToString("")
  code = code.replace("`", backtrick)
  code = """Function(ChromeXt_decode(`${code}`))();"""

  when (script.runAt) {
    RunAt.START -> {}
    RunAt.END -> code = """document.addEventListener("DOMContenLoaded",(_e)=>{${code})};"""
    RunAt.IDLE -> code = """window.onload=(_e)=>{setTimeout(()=>{${code}},20)};"""
  }

  script.grant.forEach {
    when (it) {
      "GM_addStyle" -> code = GM_addStyle + code
    }
  }

  // Add decode function, and it finally contains only three backtricks in total
  code = """function ChromeXt_decode(src) {return src.replaceAll("${backtrick}", "`");};""" + code
  return code
}
