package org.matrix.chromext.proxy

import java.net.URLEncoder
import org.matrix.chromext.Chrome
import org.matrix.chromext.script.Script
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.script.encodeScript
import org.matrix.chromext.script.kMaxURLChars
import org.matrix.chromext.script.urlMatch
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.invokeMethod

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
  val mTab = tabWebContentsDelegateAndroidImpl.getDeclaredField("a")

  private val mUrl = loadUrlParams.getDeclaredField("a")
  private val mVerbatimHeaders =
      loadUrlParams.getDeclaredFields().filter { it.getType() == String::class.java }.elementAt(1)
  private val mSpec = gURL.getDeclaredField("a")

  private fun loadUrl(url: String) {
    TabModel.getTab()?.invokeMethod(newUrl(url)) {
      getParameterCount() == 1 &&
          getParameterTypes().first() == loadUrlParams &&
          getReturnType() == Int::class.java
    }
  }

  private fun newUrl(url: String): Any {
    val constructor = loadUrlParams.getDeclaredConstructors().first()
    val types = constructor.getParameterTypes()
    if (types.size == 2 && types.first() == Int::class.java) {
      return constructor.newInstance(0, url)
    } else if (types.size == 2 && types.first() == String::class.java) {
      return constructor.newInstance(url, 0)
    } else {
      return constructor.newInstance(url)
    }
  }

  private fun invokeScript(url: String) {
    ScriptDbManager.scripts.forEach loop@{
      val script = it
      script.exclude.forEach {
        if (urlMatch(it, url, true)) {
          return@loop
        }
      }
      script.match.forEach {
        if (urlMatch(it, url, false)) {
          evaluateJavaScript(script)
          Log.i("${script.id} injected")
          return@loop
        }
      }
    }
  }

  private fun evaluateJavaScript(script: Script) {
    val code = encodeScript(script)
    if (code != null) {
      evaluateJavaScript(code)
      Log.d("Run script: ${script.code.replace("\\s+".toRegex(), " ")}")
    }
  }

  fun evaluateJavaScript(script: String, forceWrap: Boolean = false) {
    if (script == "") return
    var code = URLEncoder.encode(script, "UTF-8").replace("+", "%20")
    if (code.length > kMaxURLChars - 20 || forceWrap) {
      val alphabet: List<Char> = ('a'..'z') + ('A'..'Z')
      val randomString = List(16) { alphabet.random() }.joinToString("")
      val backtrick = List(16) { alphabet.random() }.joinToString("")
      val dollarsign = List(16) { alphabet.random() }.joinToString("")
      loadUrl("javascript: void(globalThis.${randomString} = '');")
      URLEncoder.encode(script.replace("`", backtrick).replace("$", dollarsign), "UTF-8")
          .replace("+", "%20")
          .chunked(kMaxURLChars - 100)
          .forEach { loadUrl("javascript: void(globalThis.${randomString} += String.raw`${it}`);") }
      loadUrl(
          "javascript: globalThis.${randomString}=globalThis.${randomString}.replaceAll('${backtrick}', '`').replaceAll('${dollarsign}', '\$');try{Function(${randomString})()}catch(e){let script=document.createElement('script');script.textContent=${randomString};document.head.append(script)};")
    } else {
      loadUrl("javascript: ${code}")
    }
  }

  fun parseUrl(packed: Any?): String? {
    if (packed == null) {
      return null
    } else if (packed::class.java == loadUrlParams) {
      return mUrl.get(packed) as String
    } else if (packed::class.java == gURL) {
      return mSpec.get(packed) as String
    }
    Log.e("parseUrl: ${packed::class.qualifiedName} is not ${loadUrlParams.name} nor ${gURL.name}")
    return null
  }

  fun userAgentHook(url: String, urlParams: Any) {
    val origin = parseOrigin(url)
    if (origin != null) {
      // Log.d("Change User-Agent header: ${origin}")
      if (ScriptDbManager.userAgents.contains(origin)) {
        mVerbatimHeaders.set(urlParams, "user-agent: ${ScriptDbManager.userAgents.get(origin)}\r\n")
      }
    }
  }

  private fun parseOrigin(url: String): String? {
    val protocol = url.split("://")
    if (protocol.size > 1 && arrayOf("https", "http", "file").contains(protocol.first())) {
      return protocol.first() + "://" + protocol.elementAt(1).split("/").first()
    } else {
      return null
    }
  }

  fun didUpdateUrl(url: String) {
    val origin = parseOrigin(url)
    if (origin != null) {
      invokeScript(url)
      if (ScriptDbManager.cosmeticFilters.contains(origin)) {
        val script =
            Chrome.getContext().assets.open("cosmetic-filter.js").bufferedReader().use {
              it.readText()
            }
        evaluateJavaScript(
            "globalThis.ChromeXt_filter=`${ScriptDbManager.cosmeticFilters.get(origin)}`;${script}")
        Log.d("Cosmetic filters applied to ${origin}")
      }
      if (ScriptDbManager.userAgents.contains(origin)) {
        evaluateJavaScript(
            "Object.defineProperties(window.navigator,{userAgent:{value:'${ScriptDbManager.userAgents.get(origin)}'}});")
      }
    }
  }
}
