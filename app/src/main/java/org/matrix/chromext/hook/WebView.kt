package org.matrix.chromext.hook

import android.os.Handler
import android.webkit.ConsoleMessage
import android.webkit.WebView
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

  fun evaluateJavascript(code: String?) {
    val webView = Chrome.getTab() as WebView?
    if (code != null && code.length > 0 && webView != null) {
      Handler(Chrome.getContext().mainLooper).post {
        val enableJS = webView.settings.javaScriptEnabled
        val enableDOM = webView.settings.domStorageEnabled
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.evaluateJavascript(code, null)
        webView.settings.javaScriptEnabled = enableJS
        webView.settings.domStorageEnabled = enableDOM
      }
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
          val consoleMessage = it.args[0] as ConsoleMessage
          if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.TIP &&
              consoleMessage.sourceId() == "" &&
              consoleMessage.lineNumber() == Local.anchorInChromeXt) {
            Listener.startAction(consoleMessage.message())
          } else {
            Log.d(
                consoleMessage.messageLevel().toString() +
                    ": [${consoleMessage.sourceId()}@${consoleMessage.lineNumber()}] ${consoleMessage.message()}")
          }
        }

    fun onUpdateUrl(url: String, view: WebView) {
      Chrome.updateTab(view)
      ScriptDbManager.invokeScript(url)
    }

    findMethod(WebView::class.java) { name == "loadUrl" }
        // public void loadUrl (String url)
        .hookAfter { onUpdateUrl(it.args[0] as String, it.thisObject as WebView) }

    findMethod(ViewClient!!, true) { name == "onPageStarted" }
        // public void onPageStarted (WebView view, String url, Bitmap favicon)
        .hookAfter { onUpdateUrl(it.args[1] as String, it.args[0] as WebView) }

    findMethod(Chrome.load("android.app.Activity")) { name == "onStop" }
        .hookBefore { ScriptDbManager.updateScriptStorage() }
  }
}
