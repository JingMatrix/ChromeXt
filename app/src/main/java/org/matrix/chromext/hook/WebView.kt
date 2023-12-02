package org.matrix.chromext.hook

import android.app.Activity
import android.os.Build
import android.os.Handler
import java.lang.ref.WeakReference
import org.matrix.chromext.Chrome
import org.matrix.chromext.Listener
import org.matrix.chromext.script.Local
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.hookAfter
import org.matrix.chromext.utils.hookBefore
import org.matrix.chromext.utils.invokeMethod

object WebViewHook : BaseHook() {

  var ViewClient: Class<*>? = null
  var ChromeClient: Class<*>? = null
  var WebView: Class<*>? = null
  val records = mutableListOf<WeakReference<Any>>()

  fun evaluateJavascript(code: String?, view: Any?) {
    val webView = (view ?: Chrome.getTab())
    if (code != null && code.length > 0 && webView != null) {
      val webSettings = webView.invokeMethod { name == "getSettings" }
      if (webSettings?.invokeMethod { name == "getJavaScriptEnabled" } == true)
          Handler(Chrome.getContext().mainLooper).post {
            webView.invokeMethod(code, null) { name == "evaluateJavascript" }
          }
    }
  }

  override fun init() {

    findMethod(ChromeClient!!, true) { name == "onConsoleMessage" }
        // public boolean onConsoleMessage (ConsoleMessage consoleMessage)
        .hookAfter {
          // This should be the way to communicate with the front-end of ChromeXt
          val chromeClient = it.thisObject
          val consoleMessage = it.args[0]
          val messageLevel = consoleMessage.invokeMethod { name == "messageLevel" }
          val sourceId = consoleMessage.invokeMethod { name == "sourceId" }
          val lineNumber = consoleMessage.invokeMethod { name == "lineNumber" }
          val message = consoleMessage.invokeMethod { name == "message" } as String
          if (messageLevel.toString() == "TIP" &&
              sourceId == "local://ChromeXt/init" &&
              lineNumber == Local.anchorInChromeXt) {
            val webView =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                  records
                      .find {
                        it.get()?.invokeMethod { name == "getWebChromeClient" } == chromeClient
                      }
                      ?.get()
                } else Chrome.getTab()
            Listener.startAction(message, webView)
          } else {
            Log.d(messageLevel.toString() + ": [${sourceId}@${lineNumber}] ${message}")
          }
        }

    fun onUpdateUrl(url: String, view: Any?) {
      if (url.startsWith("javascript") || view == null) return
      Chrome.updateTab(view)
      ScriptDbManager.invokeScript(url, view)
    }

    findMethod(WebView!!) { name == "setWebChromeClient" }
        .hookAfter {
          val webView = it.thisObject
          records.removeAll(records.filter { it.get() == null || it.get() == webView })
          if (it.args[0] != null) records.add(WeakReference(webView))
        }

    findMethod(WebView!!) { name == "onAttachedToWindow" }
        .hookAfter { Chrome.updateTab(it.thisObject) }

    findMethod(ViewClient!!, true) { name == "onPageStarted" }
        // public void onPageStarted (WebView view, String url, Bitmap favicon)
        .hookAfter { onUpdateUrl(it.args[1] as String, it.args[0]) }

    findMethod(Activity::class.java) { name == "onStop" }
        .hookBefore { ScriptDbManager.updateScriptStorage() }
  }
}
