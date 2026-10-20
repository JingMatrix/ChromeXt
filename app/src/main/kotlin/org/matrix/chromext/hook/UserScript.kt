package org.matrix.chromext.hook

import android.content.Context
import android.net.http.HttpResponseCache
import org.matrix.chromext.Chrome
import org.matrix.chromext.Listener
import org.matrix.chromext.proxy.UserScriptProxy
import org.matrix.chromext.script.Local
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.findMethodOrNull
import org.matrix.chromext.utils.hookAfter
import org.matrix.chromext.utils.hookBefore

object UserScriptHook : BaseHook() {

  override fun init() {

    val proxy = UserScriptProxy

    // proxy.tabModelJniBridge.declaredConstructors[0].hookAfter {
    //   Chrome.addTabModel(it.thisObject)
    // }

    // findMethod(proxy.tabModelJniBridge) { name == "destroy" }
    //     .hookBefore { Chrome.dropTabModel(it.thisObject) }

    if (proxy.mIsLoading == null) {
      // The field could not be located, so track the loading state ourselves. The name of
      // loadingStateChanged survives the obfuscation because it is called from the native side, and
      // it is the only writer of that field.
      runCatching {
        findMethod(proxy.tabWebContentsDelegateAndroidImpl) { name == "loadingStateChanged" }
            // public void loadingStateChanged(boolean toDifferentDocument)
            .hookAfter { proxy.setLoading(proxy.getTab(it.thisObject), it.args[0] as Boolean) }
        proxy.startTrackingLoadingState()
      }
          .onFailure { Log.ex(it, "Fail to track the loading state of tabs") }
    }

    findMethod(proxy.tabWebContentsDelegateAndroidImpl) {
          name == "onUpdateUrl" || name == "onUpdateTargetUrl"
        }
        // public void onUpdateTargetUrl(GURL url)
        .hookAfter {
          val tab = proxy.getTab(it.thisObject)!!
          Chrome.updateTab(tab)
          var url = proxy.parseUrl(it.args[0])!!
          if (url.isEmpty() && proxy.getUrl != null) {
            url = proxy.parseUrl(proxy.getUrl(tab))!!
          }
          if (!url.startsWith("chrome") && proxy.isLoading(tab)) {
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
              sourceId.startsWith("local://ChromeXt/init") &&
              lineNumber == Local.anchorInChromeXt) {
            Listener.startAction(it.args[1] as String, proxy.getTab(it.thisObject), null, sourceId)
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

    (findMethodOrNull(proxy.navigationControllerImpl) { name == "loadUrl" }
            ?: findMethodOrNull(proxy.navigationControllerImpl) {
              parameterTypes contentDeepEquals arrayOf(proxy.loadUrlParams) &&
                  returnType.simpleName == "NavigationHandle"
            }
            ?: findMethod(proxy.navigationControllerImpl) {
              parameterTypes contentDeepEquals arrayOf(proxy.loadUrlParams)
            })
        // public NavigationHandle loadUrl(LoadUrlParams params)
        .hookBefore {
          val url = proxy.parseUrl(it.args[0])!!
          proxy.userAgentHook(url, it.args[0])
        }

    findMethod(proxy.chromeTabbedActivity, true) { name == "onResume" }
        .hookBefore { Chrome.init(it.thisObject as Context) }

    // Some browsers declare neither onResume nor onStop on their own activity, both come from the
    // superclass
    findMethod(proxy.chromeTabbedActivity, true) { name == "onStop" }
        .hookBefore {
          ScriptDbManager.updateScriptStorage()
          val cache = HttpResponseCache.getInstalled()
          Log.d("HttpResponseCache: Hit ${cache.hitCount} / NetWork ${cache.networkCount}")
          cache.flush()
        }
    isInit = true
  }
}
