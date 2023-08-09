package org.matrix.chromext.hook

import android.content.Context
import org.matrix.chromext.Chrome
import org.matrix.chromext.Listener
import org.matrix.chromext.proxy.UserScriptProxy
import org.matrix.chromext.script.Local
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.hookAfter
import org.matrix.chromext.utils.hookBefore

object UserScriptHook : BaseHook() {

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
          val tab = proxy.getTab(it.thisObject)!!
          Chrome.updateTab(tab)
          val url = proxy.parseUrl(it.args[0])!!
          if (!url.startsWith("chrome")) {
            ScriptDbManager.invokeScript(url)
          }
        }

    findMethod(proxy.tabWebContentsDelegateAndroidImpl) { name == "addMessageToConsole" }
        // public boolean addMessageToConsole(int level, String message, int lineNumber,
        // String sourceId)
        .hookAfter {
          // This should be the way to communicate with the front-end of ChromeXt
          val lineNumber = it.args[2] as Int
          val sourceId = it.args[3] as String
          if (it.args[0] as Int == 0 &&
              sourceId.length == 0 &&
              lineNumber == Local.anchorInChromeXt) {
            Listener.startAction(it.args[1] as String, proxy.getTab(it.thisObject))
          } else {
            Log.d(
                when (it.args[0] as Int) {
                  0 -> "D"
                  2 -> "W"
                  3 -> "E"
                  else -> "V"
                } + ": [${sourceId}@${lineNumber}] ${it.args[1]}")
          }
        }

    findMethod(proxy.navigationControllerImpl) {
          name == "loadUrl" || getParameterTypes() contentDeepEquals arrayOf(proxy.loadUrlParams)
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
