package org.matrix.chromext.hook

import android.content.Context
import kotlin.concurrent.thread
import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.DEV_FRONT_END
import org.matrix.chromext.proxy.UserScriptProxy
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.ResourceMerge
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.hookAfter
import org.matrix.chromext.utils.hookBefore

object UserScriptHook : BaseHook() {

  override fun init() {

    val proxy = UserScriptProxy

    val ctx = Chrome.getContext()
    ResourceMerge.enrich(ctx)
    val promptInstallUserScript =
        ctx.assets.open("editor.js").bufferedReader().use { it.readText() }
    val customizeDevTool = ctx.assets.open("devtools.js").bufferedReader().use { it.readText() }
    val cosmeticFilter =
        ctx.assets.open("cosmetic-filter.js").bufferedReader().use { it.readText() }

    proxy.tabModelJniBridge.getDeclaredConstructors()[0].hookAfter {
      Chrome.addTabModel(it.thisObject)
    }

    findMethod(proxy.tabModelJniBridge) { name == "destroy" }
        .hookBefore { Chrome.dropTabModel(it.thisObject) }

    findMethod(proxy.tabWebContentsDelegateAndroidImpl) { name == "onUpdateUrl" }
        // public void onUpdateUrl(GURL url)
        .hookAfter {
          Chrome.refreshTab(proxy.mTab.get(it.thisObject))
          val url = proxy.parseUrl(it.args[0])!!
          proxy.evaluateJavascript("globalThis.ChromeXt=console.debug.bind(console);")
          if (url.endsWith(".user.js")) {
            proxy.evaluateJavascript(promptInstallUserScript)
          } else if (url.startsWith(DEV_FRONT_END)) {
            proxy.evaluateJavascript(customizeDevTool)
          } else if (!url.endsWith("/ChromeXt/")) {
            thread {
              proxy.invokeScript(url)
              val origin = proxy.parseOrigin(url)
              if (origin != null) {
                if (ScriptDbManager.cosmeticFilters.contains(origin)) {
                  proxy.evaluateJavascript(
                      "globalThis.ChromeXt_filter=`${ScriptDbManager.cosmeticFilters.get(origin)}`;${cosmeticFilter}")
                  Log.d("Cosmetic filters applied to ${origin}")
                }
                if (ScriptDbManager.userAgents.contains(origin)) {
                  proxy.evaluateJavascript(
                      "Object.defineProperties(window.navigator,{userAgent:{value:'${ScriptDbManager.userAgents.get(origin)}'}});")
                }
              }
            }
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
                          Chrome.refreshTab(proxy.mTab.get(it.thisObject))
                          proxy.evaluateJavascript(callback)
                        }
                      }
                      .onFailure {
                        Log.w("Failed with ${action}: ${payload}")
                        Log.ex(it)
                      }
                }
                .onFailure { Log.d("Ignore console.debug: " + text) }
          } else {
            Log.d(
                when (it.args[0] as Int) {
                  0 -> "D"
                  2 -> "W"
                  3 -> "E"
                  else -> "V"
                } + ": [${it.args[3]}@${it.args[2]}] ${it.args[1]}")
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

    findMethod(proxy.chromeTabbedActivity, true) { name == "onResume" }
        .hookBefore { Chrome.init(it.thisObject as Context) }

    findMethod(proxy.chromeTabbedActivity) { name == "onStop" }
        .hookBefore { ScriptDbManager.updateScriptStorage() }
  }
}
