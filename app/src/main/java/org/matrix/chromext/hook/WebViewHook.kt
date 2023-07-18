package org.matrix.chromext.hook

import android.os.Handler
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebView
import de.robv.android.xposed.XC_MethodHook.Unhook
import java.lang.ref.WeakReference
import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.Listener
import org.matrix.chromext.devtools.DEV_FRONT_END
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.script.openEruda
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.ResourceMerge
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

    val ctx = Chrome.getContext()
    ResourceMerge.enrich(ctx)
    val promptInstallUserScript =
        ctx.assets.open("editor.js").bufferedReader().use { it.readText() }
    val customizeDevTool = ctx.assets.open("devtools.js").bufferedReader().use { it.readText() }

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
            val text = consoleMessage.message()
            runCatching {
                  val data = JSONObject(text)
                  val action = data.getString("action")
                  val payload = data.getString("payload")
                  runCatching { evaluateJavascript(Listener.on(action, payload)) }
                      .onFailure { Log.w("Failed with ${action}: ${payload}") }
                }
                .onFailure { Log.d("Ignore console.debug: " + text) }
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
      evaluateJavascript("globalThis.ChromeXt=console.debug.bind(console);")
      if (url.endsWith(".user.js")) {
        evaluateJavascript(promptInstallUserScript)
      } else if (url.startsWith(DEV_FRONT_END)) {
        view.settings.userAgentString = null
        evaluateJavascript(customizeDevTool)
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

    var contextMenuHook: Unhook? = null
    findMethod(View::class.java) { name == "startActionMode" && getParameterTypes().size == 2 }
        // public ActionMode startActionMode (ActionMode.Callback callback,
        //         int type)
        .hookBefore {
          if (it.args[1] as Int == ActionMode.TYPE_FLOATING && it.thisObject is WebView) {
            val view = it.thisObject as WebView
            val isChromeXt = view.getUrl()!!.endsWith("/ChromeXt/")
            webView = WeakReference(view)
            contextMenuHook?.unhook()
            contextMenuHook =
                findMethod(it.args[0]::class.java) { name == "onCreateActionMode" }
                    // public abstract boolean onCreateActionMode (ActionMode mode, Menu menu)
                    .hookAfter {
                      val mode = it.args[0] as ActionMode
                      val menu = it.args[1] as Menu
                      val erudaMenu = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, "Eruda console")
                      if (isChromeXt) {
                        erudaMenu.setTitle("Developer tools")
                      }
                      erudaMenu.setOnMenuItemClickListener(
                          MenuItem.OnMenuItemClickListener {
                            if (isChromeXt) {
                              Listener.on("inspectPages")
                            } else {
                              evaluateJavascript(openEruda)
                            }
                            mode.finish()
                            true
                          })
                    }
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
