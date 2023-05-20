package org.matrix.chromext.hook

import android.webkit.ConsoleMessage
import android.webkit.WebView
import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.script.encodeScript
import org.matrix.chromext.script.urlMatch
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.ResourceMerge
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.hookAfter

object WebWiewHook : BaseHook() {

  var ViewClient: Class<*>? = null
  var ChromeClient: Class<*>? = null

  override fun init() {

    val ctx = Chrome.getContext()
    ResourceMerge.enrich(ctx)
    val promptInstallUserScript =
        ctx.assets.open("editor.js").bufferedReader().use { it.readText() }
    val cosmeticFilter =
        ctx.assets.open("cosmetic-filter.js").bufferedReader().use { it.readText() }

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
                  runCatching { ScriptDbManager.on(action, payload) }
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
      view.settings.javaScriptEnabled = true
      view.evaluateJavascript("globalThis.ChromeXt=console.debug.bind(console);", null)
      if (url.endsWith(".user.js")) {
        view.evaluateJavascript(promptInstallUserScript, null)
      } else if (!url.endsWith("/ChromeXt/")) {
        val protocol = url.split("://")
        if (protocol.size > 1 && arrayOf("https", "http", "file").contains(protocol.first())) {
          ScriptDbManager.scripts.forEach loop@{
            val script = it
            script.exclude.forEach {
              if (urlMatch(it, url, true)) {
                return@loop
              }
            }
            script.match.forEach {
              if (urlMatch(it, url, false)) {
                val code = encodeScript(script)
                if (code != null) {
                  Log.i("${script.id} injected")
                  view.evaluateJavascript(code, null)
                  Log.d("Run script: ${script.code.replace("\\s+".toRegex(), " ")}")
                }
                return@loop
              }
            }
          }

          val origin = protocol.first() + "://" + protocol[1].split("/").first()
          if (ScriptDbManager.cosmeticFilters.contains(origin)) {
            view.evaluateJavascript(
                "globalThis.ChromeXt_filter=`${ScriptDbManager.cosmeticFilters.get(origin)}`;${cosmeticFilter}",
                null)
            Log.d("Cosmetic filters applied to ${origin}")
          }
          if (ScriptDbManager.userAgents.contains(origin)) {
            view.evaluateJavascript(
                "Object.defineProperties(window.navigator,{userAgent:{value:'${ScriptDbManager.userAgents.get(origin)}'}});",
                null)
          }
        }
        view.settings.javaScriptEnabled = enableJS
      }
    }

    findMethod(WebView::class.java) { name == "loadUrl" }
        // public void loadUrl (String url)
        .hookAfter { onUpdateUrl(it.args[0] as String, it.thisObject as WebView) }

    findMethod(ViewClient!!, true) { name == "onPageStarted" }
        // public void onPageStarted (WebView view, String url, Bitmap favicon)
        .hookAfter { onUpdateUrl(it.args[1] as String, it.args[0] as WebView) }
  }
}
