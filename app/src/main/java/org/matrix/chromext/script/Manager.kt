package org.matrix.chromext.script

import android.content.ContentValues
import android.content.Context
import android.database.AbstractWindowedCursor
import android.database.CursorWindow
import android.net.Uri
import android.os.Build
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.hook.WebViewHook
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.isChromeXtFrontEnd
import org.matrix.chromext.utils.isDevToolsFrontEnd
import org.matrix.chromext.utils.isUserScript
import org.matrix.chromext.utils.matching
import org.matrix.chromext.utils.parseOrigin
import org.matrix.chromext.utils.resolveContentUrl
import org.matrix.chromext.utils.shouldBypassSandbox

object ScriptDbManager {

  val scripts = query()
  val cosmeticFilters: MutableMap<String, String>
  val userAgents: MutableMap<String, String>
  val cspRules: MutableMap<String, String>

  init {
    val ctx = Chrome.getContext()
    @Suppress("UNCHECKED_CAST")
    cosmeticFilters =
        ctx.getSharedPreferences("CosmeticFilter", Context.MODE_PRIVATE).getAll()
            as MutableMap<String, String>
    @Suppress("UNCHECKED_CAST")
    userAgents =
        ctx.getSharedPreferences("UserAgent", Context.MODE_PRIVATE).getAll()
            as MutableMap<String, String>
    @Suppress("UNCHECKED_CAST")
    cspRules =
        ctx.getSharedPreferences("CSPRule", Context.MODE_PRIVATE).getAll()
            as MutableMap<String, String>
  }

  fun insert(vararg script: Script) {
    val dbHelper = ScriptDbHelper(Chrome.getContext())
    val db = dbHelper.writableDatabase
    script.forEach {
      val lines = db.delete("script", "id = ?", arrayOf(it.id))
      if (lines > 0) {
        Log.d("Update ${lines.toString()} rows with id ${it.id}")
      }
      val values =
          ContentValues().apply {
            put("id", it.id)
            put("code", it.code)
            put("meta", it.meta)
            if (it.storage != null) {
              put("storage", it.storage!!.toString())
            }
          }
      runCatching { db.insertOrThrow("script", null, values) }
          .onFailure {
            Log.e("Fail to store script ${values.getAsString("id")} into SQL database.")
            Log.ex(it)
          }
    }
    dbHelper.close()
  }

  fun invokeScript(url: String) {
    val codes = mutableListOf<String>(Local.initChromeXt)
    val path = resolveContentUrl(url)
    if (path != null && (Chrome.isSamsung || !path.startsWith("/"))) {
      val text =
          Chrome.getContext()
              .contentResolver
              .openInputStream(Uri.parse(url))
              ?.bufferedReader()
              ?.readText()
      if (text != null) {
        val data = JSONObject(mapOf("utf-8" to text))
        codes.add("window.content=${data};")
        codes.add(Local.encoding)
        codes.add("fixEncoding();")
      }
    }
    if (url.endsWith(".txt") && codes.size == 1) {
      codes.add(Local.encoding)
      codes.add("fixEncoding();")
    }
    val webSettings =
        if (WebViewHook.isInit) {
          (Chrome.getTab() as WebView?)?.settings
        } else {
          null
        }
    var runScripts = false
    var bypassSandbox = false
    if (isUserScript(url)) {
      if (codes.size == 1) codes.add(Local.encoding)
      codes.add(Local.promptInstallUserScript)
      bypassSandbox = shouldBypassSandbox(url)
      webSettings?.javaScriptEnabled = true
    } else if (isDevToolsFrontEnd(url)) {
      codes.add(Local.customizeDevTool)
      webSettings?.userAgentString = null
      webSettings?.javaScriptEnabled = true
    } else if (!isChromeXtFrontEnd(url)) {
      val origin = parseOrigin(url)
      if (origin != null) {
        if (cspRules.contains(origin)) {
          runCatching {
            val rule = JSONArray(cspRules.get(origin))
            codes.add("ChromeXt.cspRules.push(...${rule});${Local.cspRule}")
          }
        }
        if (cosmeticFilters.contains(origin)) {
          runCatching {
            val filter = JSONArray(cosmeticFilters.get(origin))
            codes.add("ChromeXt.filters.push(...${filter});${Local.cosmeticFilter}")
          }
        }
        if (userAgents.contains(origin)) {
          val agent = userAgents.get(origin)
          codes.add("Object.defineProperties(window.navigator,{userAgent:{value:'${agent}'}});")
          webSettings?.userAgentString = agent
        }
        runScripts = true
      }
    }
    if (runScripts) codes.add("ChromeXt.lock(${Local.key});")
    codes.add("//# sourceURL=local://ChromeXt/init")
    webSettings?.javaScriptEnabled = true
    Chrome.evaluateJavascript(listOf(codes.joinToString("\n")), null, bypassSandbox, bypassSandbox)
    if (runScripts) {
      codes.clear()
      scripts.filter { matching(it, url) }.forEach { codes.addAll(GM.bootstrap(it)) }
      Chrome.evaluateJavascript(codes)
    }
  }

  fun updateScriptStorage() {
    val dbHelper = ScriptDbHelper(Chrome.getContext())
    val db = dbHelper.writableDatabase
    scripts.forEach {
      if (it.storage != null) {
        val values = ContentValues().apply { put("storage", it.storage.toString()) }
        if (db.update("script", values, "id = ?", arrayOf(it.id)).toString() == "-1") {
          Log.e("Updating scriptStorage failed for: " + it.id)
        } else {
          Log.d("ScriptStorage updated for " + it.id)
        }
      }
    }
    dbHelper.close()
  }

  private fun query(
      selection: String? = null,
      selectionArgs: Array<String>? = null,
  ): MutableList<Script> {
    val dbHelper = ScriptDbHelper(Chrome.getContext())
    val db = dbHelper.readableDatabase
    val cursor = db.query("script", null, selection, selectionArgs, null, null, null)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      (cursor as AbstractWindowedCursor).setWindow(CursorWindow("max", 1024 * 1024 * 10.toLong()))
    }
    val quried_scripts = mutableListOf<Script>()
    with(cursor) {
      while (moveToNext()) {
        val id = getString(getColumnIndexOrThrow("id"))
        val meta = getString(getColumnIndexOrThrow("meta"))
        val code = getString(getColumnIndexOrThrow("code"))
        val storage = getString(getColumnIndexOrThrow("storage"))
        val script = parseScript(meta + code, storage)
        if (script != null && script.id == id) {
          quried_scripts.add(script)
        }
      }
    }
    cursor.close()
    dbHelper.close()
    return quried_scripts
  }
}
