package org.matrix.chromext.proxy

import android.net.Uri
import android.os.Handler
import org.matrix.chromext.Chrome
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.findMethod

object UserScriptProxy {
  // It is possible to do a HTTP POST with LoadUrlParams Class
  // grep org/chromium/content_public/common/ResourceRequestBody to get setPostData in
  // org/chromium/content_public/browser/LoadUrlParams

  val gURL = Chrome.load("org.chromium.url.GURL")
  val loadUrlParams = Chrome.load("org.chromium.content_public.browser.LoadUrlParams")
  val tabModelJniBridge = Chrome.load("org.chromium.chrome.browser.tabmodel.TabModelJniBridge")
  val tabWebContentsDelegateAndroidImpl =
      Chrome.load("org.chromium.chrome.browser.tab.TabWebContentsDelegateAndroidImpl")
  val navigationControllerImpl =
      Chrome.load("org.chromium.content.browser.framehost.NavigationControllerImpl")
  val chromeTabbedActivity = Chrome.load("org.chromium.chrome.browser.ChromeTabbedActivity")
  val mTab = tabWebContentsDelegateAndroidImpl.getDeclaredField("a")
  val loadUrl =
      findMethod(Chrome.load("org.chromium.chrome.browser.tab.TabImpl")) {
        getParameterTypes() contentDeepEquals arrayOf(loadUrlParams) &&
            getReturnType() == Int::class.java
      }

  private val mUrl = loadUrlParams.getDeclaredField("a")
  private val mVerbatimHeaders =
      loadUrlParams.getDeclaredFields().filter { it.getType() == String::class.java }[1]
  private val mSpec = gURL.getDeclaredField("a")

  private fun loadUrl(url: String, tab: Any? = Chrome.getTab()) {
    Handler(Chrome.getContext().getMainLooper()).post {
      runCatching { loadUrl.invoke(tab, newLoadUrlParams(url)) }.onFailure { Log.ex(it) }
    }
  }

  fun newLoadUrlParams(url: String): Any {
    val constructor = loadUrlParams.getDeclaredConstructors().first()
    val types = constructor.getParameterTypes()
    if (types contentDeepEquals arrayOf(Int::class.java, String::class.java)) {
      return constructor.newInstance(0, url)
    } else if (types contentDeepEquals arrayOf(String::class.java, Int::class.java)) {
      return constructor.newInstance(url, 0)
    } else {
      return constructor.newInstance(url)
    }
  }

  fun evaluateJavascript(script: String, tab: Any? = Chrome.getTab()) {
    if (script == "") return
    val code = Uri.encode(script)
    loadUrl("javascript: ${code}", tab)
  }

  fun parseUrl(packed: Any?): String? {
    if (packed == null) {
      return null
    } else if (packed::class.java == loadUrlParams) {
      return mUrl.get(packed) as String
    } else if (packed::class.java == gURL) {
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
        mVerbatimHeaders.set(urlParams, "user-agent: ${ScriptDbManager.userAgents.get(origin)}\r\n")
        return true
      }
    }
    return false
  }

  fun parseOrigin(url: String): String? {
    val protocol = url.split("://")
    if (protocol.size > 1 && arrayOf("https", "http", "file").contains(protocol.first())) {
      return protocol.first() + "://" + protocol[1].split("/").first()
    } else {
      return null
    }
  }
}
