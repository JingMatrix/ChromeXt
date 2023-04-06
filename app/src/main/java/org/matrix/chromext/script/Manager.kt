package org.matrix.chromext.script

import android.content.ContentValues
import android.content.Context
import android.database.AbstractWindowedCursor
import android.database.CursorWindow
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import org.matrix.chromext.Chrome
import org.matrix.chromext.DevTools
import org.matrix.chromext.utils.Log

private const val SEP = ""

class ScriptDbManger(ctx: Context) {

  private val dbHelper: SQLiteOpenHelper
  val scripts: MutableList<Script>

  init {
    dbHelper = ScriptDbHelper(ctx)
    scripts = query()
  }

  fun insert(vararg script: Script) {
    val db = dbHelper.writableDatabase
    script.forEach {
      val lines = db.delete("script", "id = ?", arrayOf(it.id))
      if (lines > 0) {
        Log.i("Update ${lines.toString()} rows with id ${it.id}")
      }
      val values =
          ContentValues().apply {
            put("id", it.id)
            put("code", it.code)
            put("meta", it.meta)
            put("runAt", it.runAt.name)
            put("match", it.match.joinToString(separator = SEP))
            put("grant", it.grant.joinToString(separator = SEP))
            put("exclude", it.exclude.joinToString(separator = SEP))
            put("require", it.require.joinToString(separator = SEP))
            put("resource", it.resource.joinToString(separator = SEP))
          }
      if (db.insert("script", null, values).toString() == "-1") {
        Log.e("Insertion failed with: " + it.id)
      }
    }
  }

  private fun query(
      selection: String? = null,
      selectionArgs: Array<String>? = null,
  ): MutableList<Script> {
    val db = dbHelper.readableDatabase
    val cursor = db.query("script", null, selection, selectionArgs, null, null, null)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      (cursor as AbstractWindowedCursor).setWindow(CursorWindow("max", 1024 * 1024 * 10))
    }
    val quried_scripts = mutableListOf<Script>()
    with(cursor) {
      while (moveToNext()) {
        val id = getString(getColumnIndexOrThrow("id"))
        val meta = getString(getColumnIndexOrThrow("meta"))
        val code = getString(getColumnIndexOrThrow("code"))
        val runAt = RunAt.valueOf(getString(getColumnIndexOrThrow("runAt")))
        val match = getString(getColumnIndexOrThrow("match")).split(SEP).toTypedArray()
        val grant = getString(getColumnIndexOrThrow("grant")).split(SEP).toTypedArray()
        val exclude = getString(getColumnIndexOrThrow("exclude")).split(SEP).toTypedArray()
        val require = getString(getColumnIndexOrThrow("require")).split(SEP).toTypedArray()
        val resource = getString(getColumnIndexOrThrow("resource")).split(SEP).toTypedArray()
        val script = Script(id, match, grant, exclude, require, resource, meta, code, runAt)
        quried_scripts.add(script)
      }
    }
    cursor.close()
    return quried_scripts
  }

  private fun delete(ids: Array<String>) {
    val db = dbHelper.writableDatabase
    db.delete("script", "id = ?", ids)
  }

  fun close() {
    dbHelper.close()
  }

  private fun parseArray(str: String): Array<String> {
    return str.removeSurrounding("[\"", "\"]")
        .split("\",\"")
        .map { it.replace("\\", "") }
        .toTypedArray()
  }

  fun on(action: String, payload: String): String? {
    var callback: String? = null
    when (action) {
      "installScript" -> {
        val script = parseScript(payload)
        if (script == null) {
          callback = "alert('Invalid UserScript');"
        } else {
          Log.i("Install script ${script.id}")
          insert(script)
          scripts.removeAll(scripts.filter { it.id == script.id })
          scripts.add(script)
        }
      }
      "getIds" -> {
        val result = scripts.map { it.id }
        callback =
            "window.dispatchEvent(new CustomEvent('script_id',{detail:[`${result.joinToString(separator = "`,`")}`]}));"
      }
      "getMetaById" -> {
        val ids = parseArray(payload)
        val result = scripts.filter { ids.contains(it.id) }.map { it.meta }
        callback =
            "window.dispatchEvent(new CustomEvent('script_meta',{detail:[`${result.joinToString(separator = "`,`")}`]}));"
      }
      "updateMetaForId" -> {
        val CHROMEXT_SPLIT = "ChromeXt_Split_For_Metadata_Update"
        val result = payload.split(CHROMEXT_SPLIT)
        val match = scripts.filter { it.id == result[0] }
        if (match.size == 1) {
          val script = match.first()
          val newScript = parseScript(result[1] + "\n" + script.code)
          insert(newScript!!)
          scripts.remove(script)
          scripts.add(newScript)
        }
      }
      "deleteScriptById" -> {
        val ids = parseArray(payload)
        delete(ids)
        scripts.removeAll(scripts.filter { ids.contains(it.id) })
      }
      "getPages" -> {
        val pages = DevTools.pages
        if (pages != "") {
          callback = "window.dispatchEvent(new CustomEvent('pages',{detail:${pages}}));"
        }
      }
      "cosmeticFilter" -> {
        val sharedPref =
            Chrome.getContext().getSharedPreferences("CosmeticFilter", Context.MODE_PRIVATE)
        val result = payload.split(";")
        Log.d("Config cosmetic filters: ${result}")
        with(sharedPref.edit()) {
          if (result.size == 1 && sharedPref.contains(result.first())) {
            remove(result.first())
          } else if (result.size == 2) {
            putString(result.first(), result.last())
          } else {
            callback = "alert('Invalid Cosmetic Filters');"
          }
          apply()
        }
      }
      "userAgent" -> {
        val sharedPref = Chrome.getContext().getSharedPreferences("UserAgent", Context.MODE_PRIVATE)
        val result = payload.split("!")
        Log.d("Config user-agent: ${result}")
        if (result.size == 2) {
          with(sharedPref.edit()) {
            putString(result.first(), result.last())
            apply()
          }
        } else {
          callback = "alert('Invalid User-Agent');"
        }
      }
    }
    return callback
  }
}
