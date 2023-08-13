package org.matrix.chromext.proxy

import android.net.Uri
import org.matrix.chromext.Chrome
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.invokeMethod
import org.matrix.chromext.utils.parseOrigin

object UserScriptProxy {
  // It is possible to do a HTTP POST with LoadUrlParams Class
  // grep org/chromium/content_public/common/ResourceRequestBody to get setPostData in
  // org/chromium/content_public/browser/LoadUrlParams

  val gURL = Chrome.load("org.chromium.url.GURL")
  val loadUrlParams =
      if (Chrome.isSamsung) {
        Chrome.load("com.sec.terrace.browser.TerraceLoadUrlParams")
      } else {
        Chrome.load("org.chromium.content_public.browser.LoadUrlParams")
      }
  // val tabModelJniBridge = Chrome.load("org.chromium.chrome.browser.tabmodel.TabModelJniBridge")
  val tabWebContentsDelegateAndroidImpl =
      if (Chrome.isSamsung) {
        Chrome.load("com.sec.terrace.Terrace")
      } else {
        Chrome.load("org.chromium.chrome.browser.tab.TabWebContentsDelegateAndroidImpl")
      }
  val navigationControllerImpl =
      Chrome.load("org.chromium.content.browser.framehost.NavigationControllerImpl")
  val chromeTabbedActivity =
      if (Chrome.isSamsung) {
        Chrome.load("com.sec.terrace.TerraceActivity")
      } else {
        Chrome.load("org.chromium.chrome.browser.ChromeTabbedActivity")
      }
  val loadUrl =
      findMethod(
          if (Chrome.isSamsung) {
            tabWebContentsDelegateAndroidImpl
          } else {
            Chrome.load("org.chromium.chrome.browser.tab.TabImpl")
          }) {
            parameterTypes contentDeepEquals arrayOf(loadUrlParams) &&
                returnType ==
                    if (Chrome.isSamsung) {
                      Void.TYPE
                    } else {
                      Int::class.java
                    }
          }

  val kMaxURLChars = 2097152

  private fun loadUrl(url: String, tab: Any? = Chrome.getTab()) {
    runCatching { loadUrl.invoke(tab, newLoadUrlParams(url)) }.onFailure { Log.ex(it) }
  }

  fun newLoadUrlParams(url: String): Any {
    val constructor = loadUrlParams.declaredConstructors.first()
    val types = constructor.parameterTypes
    if (types contentDeepEquals arrayOf(Int::class.java, String::class.java)) {
      return constructor.newInstance(0, url)
    } else if (types contentDeepEquals arrayOf(String::class.java, Int::class.java)) {
      return constructor.newInstance(url, 0)
    } else {
      return constructor.newInstance(url)
    }
  }

  fun evaluateJavascript(script: String, tab: Any? = Chrome.getTab()): Boolean {
    if (script == "") return true
    if (Chrome.isSamsung) {
      (tab ?: Chrome.getTab())?.invokeMethod(script, null) { name == "evaluateJavaScriptForTests" }
      return true
    }
    val code = Uri.encode(script)
    if (code.length < kMaxURLChars - 200) {
      loadUrl("javascript:${code}", tab ?: Chrome.getTab())
      return true
    } else {
      Log.d("evaluateJavascript fails with loadUrl")
      return false
    }
  }

  fun getTab(delegate: Any): Any? {
    if (Chrome.isSamsung) {
      return delegate
    } else {
      val mTab = tabWebContentsDelegateAndroidImpl.getDeclaredField("a")
      return mTab.get(delegate)
    }
  }

  fun parseUrl(packed: Any?): String? {
    if (packed == null) {
      return null
    } else if (packed::class.java == String::class.java) {
      return packed as String
    } else if (packed::class.java == loadUrlParams) {
      val mUrl = loadUrlParams.getDeclaredField("a")
      return mUrl.get(packed) as String
    } else if (packed::class.java == gURL) {
      val mSpec = gURL.getDeclaredField("a")
      return mSpec.get(packed) as String
    }
    Log.e("parseUrl: ${packed::class.java} is not ${loadUrlParams.name} nor ${gURL.name}")
    return null
  }

  fun userAgentHook(url: String, urlParams: Any): Boolean {
    val origin = parseOrigin(url)
    if (origin != null) {
      // Log.d("Change User-Agent header: ${origin}")
      if (ScriptDbManager.userAgents.contains(origin)) {
        val header = "user-agent: ${ScriptDbManager.userAgents.get(origin)}\r\n"
        if (Chrome.isSamsung) {
          urlParams.invokeMethod(header) { name == "setVerbatimHeaders" }
        } else {
          val mVerbatimHeaders =
              loadUrlParams.declaredFields.filter { it.type == String::class.java }[1]
          mVerbatimHeaders.set(urlParams, header)
        }
        return true
      }
    }
    return false
  }
}
