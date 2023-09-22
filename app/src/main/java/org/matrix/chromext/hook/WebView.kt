package org.matrix.chromext.hook

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import java.lang.ref.WeakReference
import org.matrix.chromext.Chrome
import org.matrix.chromext.Listener
import org.matrix.chromext.script.Local
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.hookAfter
import org.matrix.chromext.utils.hookBefore

object WebViewHook : BaseHook() {

  var ViewClient: Class<*>? = null
  var ChromeClient: Class<*>? = null
  val records = mutableListOf<WeakReference<WebView>>()

  fun evaluateJavascript(code: String?, view: Any?) {
    val webView = (view ?: Chrome.getTab()) as WebView?
    if (code != null && code.length > 0 && webView != null && webView.settings.javaScriptEnabled) {
      Handler(Chrome.getContext().mainLooper).post { webView.evaluateJavascript(code, null) }
    }
  }

  override fun init() {

    WebView.setWebContentsDebuggingEnabled(true)

    findMethod(ChromeClient!!, true) {
          name == "onConsoleMessage" &&
              parameterTypes contentDeepEquals arrayOf(ConsoleMessage::class.java)
        }
        // public boolean onConsoleMessage (ConsoleMessage consoleMessage)
        .hookAfter {
          // This should be the way to communicate with the front-end of ChromeXt
          val chromeClient = it.thisObject as WebChromeClient
          val consoleMessage = it.args[0] as ConsoleMessage
          if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.TIP &&
              consoleMessage.sourceId() == "local://ChromeXt/init" &&
              consoleMessage.lineNumber() == Local.anchorInChromeXt) {
            val webView =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                  records.find { it.get()?.getWebChromeClient() == chromeClient }?.get()
                } else Chrome.getTab() as WebView?
            Listener.startAction(consoleMessage.message(), webView)
          } else {
            Log.d(
                consoleMessage.messageLevel().toString() +
                    ": [${consoleMessage.sourceId()}@${consoleMessage.lineNumber()}] ${consoleMessage.message()}")
          }
        }

    fun onUpdateUrl(url: String, view: WebView) {
      if (url.startsWith("javascript")) return
      Chrome.updateTab(view)
      ScriptDbManager.invokeScript(url, view)
    }

    findMethod(WebView::class.java) { name == "setWebChromeClient" }
        .hookAfter {
          val webView = it.thisObject as WebView
          records.removeAll(records.filter { it.get() == null || it.get() == webView })
          if (it.args[0] != null) records.add(WeakReference(webView))
        }

    findMethod(WebView::class.java) { name == "onAttachedToWindow" }
        .hookAfter { Chrome.updateTab(it.thisObject as WebView) }

    findMethod(ViewClient!!, true) { name == "onPageStarted" }
        // public void onPageStarted (WebView view, String url, Bitmap favicon)
        .hookAfter { onUpdateUrl(it.args[1] as String, it.args[0] as WebView) }

    findMethod(Activity::class.java) { name == "onStop" }
        .hookBefore { ScriptDbManager.updateScriptStorage() }
  }
}
