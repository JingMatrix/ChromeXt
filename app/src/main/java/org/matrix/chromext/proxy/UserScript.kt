package org.matrix.chromext.proxy

import android.net.Uri
import org.matrix.chromext.Chrome
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.findField
import org.matrix.chromext.utils.findFieldOrNull
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.findMethodOrNull
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
  val tabImpl =
      if (Chrome.isSamsung) {
        tabWebContentsDelegateAndroidImpl
      } else {
        Chrome.load("org.chromium.chrome.browser.tab.TabImpl")
      }
  private val mId =
      tabImpl.declaredFields
          .run {
            val target = find { it.name == "mId" }
            if (target == null) {
              val profile = Chrome.load("org.chromium.chrome.browser.profiles.Profile")
              val startIndex = indexOfFirst { it.type == gURL }
              val endIndex = indexOfFirst { it.type == profile }
              slice(startIndex..endIndex).findLast { it.type == Int::class.java }!!
            } else target
          }
          .also { it.isAccessible = true }
  private val getId = findMethodOrNull(tabImpl) { name == "getId" }
  val mNativeAndroid = findField(tabImpl) { type == Long::class.java }
  val mTab = findFieldOrNull(tabWebContentsDelegateAndroidImpl) { type == tabImpl }
  val mIsLoading =
      tabImpl.declaredFields
          .run {
            val target = find { it.name == "mIsLoading" }
            if (target == null) {
              val webContents = Chrome.load("org.chromium.content_public.browser.WebContents")
              val anchorIndex =
                  maxOf(
                      indexOfFirst { it.type == loadUrlParams },
                      indexOfFirst { it.type == webContents })
              slice(anchorIndex..size - 1).find { it.type == Boolean::class.java }!!
            } else target
          }
          .also { it.isAccessible = true }
  val loadUrl =
      findMethod(tabImpl) {
        parameterTypes contentDeepEquals arrayOf(loadUrlParams) &&
            (Chrome.isSamsung || returnType != Void.TYPE)
      }

  val kMaxURLChars = 2097152

  private fun loadUrl(url: String, tab: Any? = Chrome.getTab()) {
    if (!Chrome.checkTab(tab)) return
    loadUrl.invoke(tab, newLoadUrlParams(url))
  }

  fun getTabId(tab: Any): String {
    val id = if (getId != null) getId.invoke(tab)!! else mId.get(tab)!!
    return id.toString()
  }

  fun newLoadUrlParams(url: String): Any {
    val constructor =
        loadUrlParams.declaredConstructors.find { it.parameterTypes.contains(String::class.java) }!!
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
    if (script.length > kMaxURLChars - 20000) return false
    val code = Uri.encode(script)
    if (code.length < kMaxURLChars - 200) {
      loadUrl("javascript:${code}", tab ?: Chrome.getTab())
      return true
    } else {
      return false
    }
  }

  fun getTab(delegate: Any): Any? {
    if (Chrome.isSamsung) {
      return delegate
    } else {
      return mTab?.get(delegate)
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
