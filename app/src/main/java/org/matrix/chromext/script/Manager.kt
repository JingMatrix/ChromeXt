package org.matrix.chromext.script

import android.content.ContentValues
import android.content.Context
import android.database.AbstractWindowedCursor
import android.database.CursorWindow
import android.os.Build
import org.matrix.chromext.Chrome
import org.matrix.chromext.utils.Log

object ScriptDbManager {

  val scripts = query()
  val cosmeticFilters: MutableMap<String, String>
  val userAgents: MutableMap<String, String>
  val cspRules: MutableMap<String, String>
  val cosmeticFilter: String

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

    cosmeticFilter = ctx.assets.open("cosmetic-filter.js").bufferedReader().use { it.readText() }
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
            put("storage", it.storage)
          }
      runCatching { db.insertOrThrow("script", null, values) }
          .onFailure {
            Log.e("Fail to store script ${values.getAsString("id")} into SQL database.")
            Log.ex(it)
          }
    }
    dbHelper.close()
  }

  fun invokeScript(url: String, origin: String) {
    val codes = mutableListOf<String>()
    if (cspRules.contains(origin)) {
      codes.add("ChromeXt.cspRules=`${cspRules.get(origin)}`;${cspRule}")
    }
    if (cosmeticFilters.contains(origin)) {
      codes.add("ChromeXt.filters=`${cosmeticFilters.get(origin)}`;${cosmeticFilter}")
    }
    if (userAgents.contains(origin)) {
      codes.add(
          "Object.defineProperties(window.navigator,{userAgent:{value:'${userAgents.get(origin)}'}});")
    }
    scripts.forEach loop@{
      val script = it
      script.exclude.forEach {
        if (urlMatch(it, url, true)) {
          return@loop
        }
      }
      script.match.forEach {
        if (urlMatch(it, url, false)) {
          Log.d("${script.id} injected")
          codes.add(GM.bootstrap(script))
        }
      }
    }
    Chrome.evaluateJavascript(codes)
  }

  fun updateScriptStorage() {
    val dbHelper = ScriptDbHelper(Chrome.getContext())
    val db = dbHelper.writableDatabase
    scripts.forEach {
      if (it.grant.contains("GM_setValue")) {
        val values = ContentValues().apply { put("storage", it.storage) }
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
