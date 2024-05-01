package org.matrix.chromext.proxy

import android.net.Uri
import android.view.ContextThemeWrapper
import android.view.View.OnAttachStateChangeListener
import org.matrix.chromext.Chrome
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.findField
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
        Chrome.load("com.sec.android.app.sbrowser.tab.LoadUrlParams")
      } else {
        Chrome.load("org.chromium.content_public.browser.LoadUrlParams")
      }
  // val tabModelJniBridge = Chrome.load("org.chromium.chrome.browser.tabmodel.TabModelJniBridge")
  val tabWebContentsDelegateAndroidImpl =
      if (Chrome.isSamsung) {
        Chrome.load("com.sec.android.app.sbrowser.tab.Tab")
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
        Chrome.load("com.sec.terrace.Terrace")
      } else {
        Chrome.load("org.chromium.chrome.browser.tab.TabImpl")
      }
  private val getId = findMethodOrNull(tabImpl) { name == "getId" }
  private val mId =
      (if (Chrome.isSamsung) tabWebContentsDelegateAndroidImpl else tabImpl)
          .declaredFields
          .run {
            val target = find { it.name == "mId" }
            if (target == null) {
              val profile = Chrome.load("org.chromium.chrome.browser.profiles.Profile")
              val windowAndroid = Chrome.load("org.chromium.ui.base.WindowAndroid")
              var startIndex = indexOfFirst { it.type == gURL }
              val endIndex = indexOfFirst {
                it.type == profile ||
                    it.type == ContextThemeWrapper::class.java ||
                    it.type == windowAndroid
              }
              if (startIndex == -1 || startIndex > endIndex) startIndex = 0
              slice(startIndex..endIndex - 1).findLast { it.type == Int::class.java }!!
            } else target
          }
          .also { it.isAccessible = true }
  val mTab = findField(tabWebContentsDelegateAndroidImpl) { type == tabImpl }
  val mIsLoading =
      tabImpl.declaredFields
          .run {
            // mIsLoading is used in method stopLoading, before calling
            // Lorg/chromium/content_public/browser/WebContents;->stop()V
            val target = find { it.name == "mIsLoading" }
            if (target == null) {
              val webContents = Chrome.load("org.chromium.content_public.browser.WebContents")
              var startIndex = indexOfFirst { it.type == webContents }
              var endIndex = indexOfFirst { it.type == OnAttachStateChangeListener::class.java }
              var alterStartIndex = indexOfFirst { it.type == loadUrlParams }
              if (endIndex < startIndex && endIndex < alterStartIndex) endIndex = size - 1
              if (startIndex < endIndex && alterStartIndex < endIndex) {
                startIndex = maxOf(startIndex, alterStartIndex)
              } else {
                startIndex = minOf(startIndex, alterStartIndex)
              }
              slice(startIndex..endIndex - 1).find { it.type == Boolean::class.java }!!
            } else target
          }
          .also { it.isAccessible = true }
  val loadUrl =
      findMethod(if (Chrome.isSamsung) tabWebContentsDelegateAndroidImpl else tabImpl) {
        parameterTypes contentDeepEquals arrayOf(loadUrlParams) &&
            (Chrome.isSamsung || returnType != Void.TYPE)
      }

  val kMaxURLChars = 2097152

  private fun loadUrl(url: String, tab: Any? = Chrome.getTab()) {
    if (!Chrome.isSamsung && !Chrome.checkTab(tab)) return
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
      mTab.get(tab ?: Chrome.getTab())?.invokeMethod(script, null) {
        name == "evaluateJavaScriptForTests"
      }
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
    return if (Chrome.isSamsung) delegate else mTab.get(delegate)
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
