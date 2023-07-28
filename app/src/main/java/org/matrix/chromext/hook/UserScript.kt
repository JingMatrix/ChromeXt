package org.matrix.chromext.hook

import android.content.Context
import kotlin.concurrent.thread
import org.matrix.chromext.Chrome
import org.matrix.chromext.Listener
import org.matrix.chromext.devtools.DEV_FRONT_END
import org.matrix.chromext.proxy.UserScriptProxy
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.ResourceMerge
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.hookAfter
import org.matrix.chromext.utils.hookBefore

object UserScriptHook : BaseHook() {

  val promptInstallUserScript: String
  val customizeDevTool: String

  init {
    val ctx = Chrome.getContext()
    ResourceMerge.enrich(ctx)
    promptInstallUserScript = ctx.assets.open("editor.js").bufferedReader().use { it.readText() }
    customizeDevTool = ctx.assets.open("devtools.js").bufferedReader().use { it.readText() }
  }

  override fun init() {

    val proxy = UserScriptProxy

    // proxy.tabModelJniBridge.getDeclaredConstructors()[0].hookAfter {
    //   Chrome.addTabModel(it.thisObject)
    // }

    // findMethod(proxy.tabModelJniBridge) { name == "destroy" }
    //     .hookBefore { Chrome.dropTabModel(it.thisObject) }

    findMethod(proxy.tabWebContentsDelegateAndroidImpl) { name == "onUpdateUrl" }
        // public void onUpdateUrl(GURL url)
        .hookAfter {
          val tab = proxy.mTab.get(it.thisObject)!!
          Chrome.refreshTab(tab)
          val url = proxy.parseUrl(it.args[0])!!
          if (url.startsWith("chrome")) {
            return@hookAfter
          }
          proxy.evaluateJavascript("globalThis.ChromeXt=console.debug.bind(console);")
          if (url.endsWith(".user.js")) {
            proxy.evaluateJavascript(promptInstallUserScript)
          } else if (url.startsWith(DEV_FRONT_END)) {
            proxy.evaluateJavascript(customizeDevTool)
          } else if (!url.endsWith("/ChromeXt/")) {
            thread {
              val origin = proxy.parseOrigin(url)
              if (origin != null) {
                ScriptDbManager.invokeScript(url, origin)
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
            Listener.startAction(it.args[1] as String, proxy.mTab.get(it.thisObject))
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
