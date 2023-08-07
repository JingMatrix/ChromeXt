package org.matrix.chromext.hook

import android.os.Handler
import android.webkit.ConsoleMessage
import android.webkit.WebView
import java.lang.ref.WeakReference
import org.matrix.chromext.Chrome
import org.matrix.chromext.Listener
import org.matrix.chromext.devtools.DEV_FRONT_END
import org.matrix.chromext.script.Local
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.hookAfter
import org.matrix.chromext.utils.hookBefore

object WebViewHook : BaseHook() {

  var ViewClient: Class<*>? = null
  var ChromeClient: Class<*>? = null
  var webView: WeakReference<WebView>? = null

  fun evaluateJavascript(code: String?) {
    Handler(Chrome.getContext().getMainLooper()).post {
      if (code != null && webView != null) {
        webView?.get()?.settings?.javaScriptEnabled = true
        webView?.get()?.settings?.domStorageEnabled = true
        webView?.get()?.evaluateJavascript(code, null)
      }
    }
  }

  override fun init() {

    WebView.setWebContentsDebuggingEnabled(true)

    findMethod(ChromeClient!!, true) {
          name == "onConsoleMessage" &&
              getParameterTypes() contentDeepEquals arrayOf(ConsoleMessage::class.java)
        }
        // public boolean onConsoleMessage (ConsoleMessage consoleMessage)
        .hookAfter {
          // This should be the way to communicate with the front-end of ChromeXt
          val consoleMessage = it.args[0] as ConsoleMessage
          if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.TIP) {
            Listener.startAction(consoleMessage.message())
          } else {
            Log.d(
                consoleMessage.messageLevel().toString() +
                    ": [${consoleMessage.sourceId()}@${consoleMessage.lineNumber()}] ${consoleMessage.message()}")
          }
        }

    fun onUpdateUrl(url: String, view: WebView) {
      val enableJS = view.settings.javaScriptEnabled
      val enableDOMStorage = view.settings.domStorageEnabled
      webView = WeakReference(view)
      evaluateJavascript(Local.initChromeXt)
      if (url.endsWith(".user.js")) {
        evaluateJavascript(Local.promptInstallUserScript)
      } else if (url.startsWith(DEV_FRONT_END)) {
        view.settings.userAgentString = null
        evaluateJavascript(Local.customizeDevTool)
      } else if (!url.endsWith("/ChromeXt/")) {
        val protocol = url.split("://")
        if (protocol.size > 1 && arrayOf("https", "http", "file").contains(protocol.first())) {
          val origin = protocol.first() + "://" + protocol[1].split("/").first()
          if (ScriptDbManager.userAgents.contains(origin)) {
            view.settings.userAgentString = ScriptDbManager.userAgents.get(origin)
          }
          ScriptDbManager.invokeScript(url, origin)
        }
        view.settings.javaScriptEnabled = enableJS
        view.settings.domStorageEnabled = enableDOMStorage
      }
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
