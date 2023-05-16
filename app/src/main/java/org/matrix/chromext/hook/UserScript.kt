package org.matrix.chromext.hook

import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.DEV_FRONT_END
import org.matrix.chromext.proxy.TabModel
import org.matrix.chromext.proxy.UserScriptProxy
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.hookAfter
import org.matrix.chromext.utils.hookBefore

object UserScriptHook : BaseHook() {

  override fun init() {

    val proxy = UserScriptProxy

    val promptInstallUserScript =
        Chrome.getContext().assets.open("editor.js").bufferedReader().use { it.readText() }
    val customizeDevTool =
        Chrome.getContext().assets.open("devtools.js").bufferedReader().use { it.readText() }

    proxy.tabModelJniBridge.getDeclaredConstructors()[0].hookAfter {
      TabModel.update(it.thisObject)
    }

    findMethod(proxy.tabModelJniBridge) { name == "destroy" }
        .hookBefore { TabModel.dropModel(it.thisObject) }

    findMethod(proxy.tabWebContentsDelegateAndroidImpl) { name == "onUpdateUrl" }
        // public void onUpdateUrl(GURL url)
        .hookAfter {
          TabModel.refresh(proxy.mTab.get(it.thisObject))
          val url = proxy.parseUrl(it.args[0])!!
          proxy.evaluateJavaScript("globalThis.ChromeXt=console.debug.bind(console);")
          if (url.endsWith(".user.js")) {
            proxy.evaluateJavaScript(promptInstallUserScript)
          } else if (url.startsWith(DEV_FRONT_END)) {
            proxy.evaluateJavaScript(customizeDevTool)
          } else if (!url.endsWith("/ChromeXt/")) {
            proxy.didUpdateUrl(url)
          }
        }

    findMethod(proxy.tabWebContentsDelegateAndroidImpl) { name == "addMessageToConsole" }
        // public boolean addMessageToConsole(int level, String message, int lineNumber,
        // String sourceId)
        .hookAfter {
          // This should be the way to communicate with the front-end of ChromeXt
          if (it.args[0] as Int == 0) {
            val text = it.args[1] as String
            runCatching {
                  val data = JSONObject(text)
                  val action = data.getString("action")
                  val payload = data.getString("payload")
                  runCatching {
                        val callback = ScriptDbManager.on(action, payload)
                        if (callback != null) {
                          TabModel.refresh(proxy.mTab.get(it.thisObject))
                          proxy.evaluateJavaScript(callback)
                        }
                      }
                      .onFailure { Log.w("Failed with ${action}: ${payload}") }
                }
                .onFailure { Log.d("Ignore console.debug: " + text) }
          } else {
            Log.d(
                when (it.args[0] as Int) {
                  0 -> "D"
                  2 -> "W"
                  3 -> "E"
                  else -> "V"
                } +
                    ": [${it.args[3]}" +
                    when (it.args[2] as Int) {
                      0 -> ""
                      else -> "@" + it.toString()
                    } +
                    "] ${it.args[1]}")
          }
        }

    findMethod(proxy.navigationControllerImpl) {
          getParameterTypes() contentDeepEquals arrayOf(proxy.loadUrlParams)
        }
        // public void loadUrl(LoadUrlParams params)
        .hookBefore {
          val url = proxy.parseUrl(it.args[0])!!
          proxy.userAgentHook(url, it.args[0])
        }
  }
}
