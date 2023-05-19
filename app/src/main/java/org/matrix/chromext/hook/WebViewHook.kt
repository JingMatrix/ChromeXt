package org.matrix.chromext.hook

import android.webkit.ConsoleMessage
import android.webkit.WebView
import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.ResourceMerge
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.hookAfter

object WebWiewHook : BaseHook() {

  var ViewClient: Class<*>? = null
  var ChromeClient: Class<*>? = null

  override fun init() {

    ResourceMerge.enrich(Chrome.getContext())
    val promptInstallUserScript =
        Chrome.getContext().assets.open("editor.js").bufferedReader().use { it.readText() }

    findMethod(ViewClient!!, true) { name == "onPageStarted" }
        // public void onPageStarted (WebView view, String url, Bitmap favicon)
        .hookAfter {
          val view = it.args[0] as WebView
          val url = it.args[1] as String
          view.evaluateJavascript("globalThis.ChromeXt=console.debug.bind(console);", null)
          if (url.endsWith(".user.js")) {
            view.evaluateJavascript(promptInstallUserScript, null)
          } else if (!url.endsWith("/ChromeXt/")) {
            Log.i(url)
          }
        }

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
                  runCatching {
                        val callback = ScriptDbManager.on(action, payload)
                        if (callback != null) {
                          // proxy.evaluateJavaScript(callback)
                        }
                      }
                      .onFailure { Log.w("Failed with ${action}: ${payload}") }
                }
                .onFailure { Log.d("Ignore console.debug: " + text) }
          } else {
            Log.d(
                consoleMessage.messageLevel().toString() +
                    ": [${consoleMessage.sourceId()}@${consoleMessage.lineNumber()}] ${consoleMessage.message()}")
          }
        }
  }
}
