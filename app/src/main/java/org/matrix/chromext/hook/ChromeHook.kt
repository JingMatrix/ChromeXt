package org.matrix.chromext.hook

import android.content.Context
import com.github.kyuubiran.ezxhelper.utils.Log
import com.github.kyuubiran.ezxhelper.utils.findAllMethods
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.hookAfter
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import com.github.kyuubiran.ezxhelper.utils.invokeMethod
import java.lang.reflect.Field
import org.matrix.chromext.script.youtubeScript

object ChromeHook : BaseHook() {
  override fun init() {
    findMethod("org.chromium.chrome.browser.base.SplitChromeApplication") {
          name == "attachBaseContext"
        }
        .hookAfter {
          val ctx: Context = (it.args[0] as Context).createContextForSplit("chrome")
          val gURL = ctx.getClassLoader().loadClass("org.chromium.url.GURL")
          val loadUrlParams =
              ctx.getClassLoader().loadClass("org.chromium.content_public.browser.LoadUrlParams")
          // .getDeclaredConstructor(String::class.java)
          val tabWebContentsDelegateAndroidImpl =
              ctx.getClassLoader()
                  .loadClass("org.chromium.chrome.browser.tab.TabWebContentsDelegateAndroidImpl")
          val interceptNavigationDelegateImpl =
              ctx.getClassLoader().loadClass(INTERCEPTNAVIGATIONDELEGATEIMPL)
          val navigationControllerImpl =
              ctx.getClassLoader()
                  .loadClass("org.chromium.content.browser.framehost.NavigationControllerImpl")
          val webContentsObserverProxy =
              ctx.getClassLoader()
                  .loadClass("org.chromium.content.browser.webcontents.WebContentsObserverProxy")
          val mUrl: Field = loadUrlParams.getDeclaredField(URL_FIELD)
          val mTab: Field = tabWebContentsDelegateAndroidImpl.getDeclaredField(TAB_FIELD)
          val mSpec: Field = gURL.getDeclaredField(SPEC_FIELD)

          findMethod(tabWebContentsDelegateAndroidImpl) { name == "onUpdateUrl" }
              .hookBefore {
                val url = mSpec.get(it.args[0]) as String
                Log.i("onUpdateUrl: ${url}")
                if (url.startsWith("https://m.youtube.com")) {
                  Log.i("Inject userscript for m.youtube.com")
                  mTab.get(it.thisObject)?.invokeMethod(
                      loadUrlParams
                          .getDeclaredConstructor(String::class.java)
                          .newInstance("javascript: ${youtubeScript}")) {
                        name == LOAD_URL
                      }
                }
              }

          findMethod(tabWebContentsDelegateAndroidImpl) { name == "addMessageToConsole" }
              .hookAfter {
                // addMessageToConsole(int level, String message, int lineNumber, String sourceId)
                Log.d("[${it.args[0]}] ${it.args[1]} @${it.args[3]}:${it.args[2]}")
              }

          findMethod(navigationControllerImpl) { name == NAVI_LOAD_URL }
              .hookBefore {
                val url = mUrl.get(it.args[0]) as String
                Log.i(
                    "loadUrl: ${url} from last visited index ${it.thisObject.invokeMethod(){name == NAVI_LAST_INDEX}}")
                if (url.startsWith("chrome://xt/")) {
                  mUrl.set(it.args[0], "javascript: 'Page reserved for ChromeXt'")
                }
              }

          findAllMethods(webContentsObserverProxy) { name == "didStartLoading" }
              .hookAfter {
                val url = mSpec.get(it.args[0]) as String
                Log.i("Start loading ${url}")
              }

          findMethod(interceptNavigationDelegateImpl) { name == "shouldIgnoreNavigation" }
              .hookBefore {
                val url = mSpec.get(it.args[1]) as String
                Log.i("ShouldIgnoreNavigation ${url} ?")
              }
        }
  }
}
