package org.matrix.chromext.script

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteOpenHelper
import org.matrix.chromext.utils.Log

private const val SEP = ""

class ScriptDbManger(ctx: Context) {

  private val dbHelper: SQLiteOpenHelper

  init {
    dbHelper = ScriptDbHelper(ctx)
  }

  fun insert(vararg script: Script) {
    val db = dbHelper.writableDatabase
    script.forEach {
      db.delete("script", "id = ?", arrayOf(it.id))
      val values =
          ContentValues().apply {
            put("id", it.id)
            put("code", it.code)
            put("meta", it.meta)
            put("runAt", it.runAt.name)
            put("shouldWrap", it.shouldWrap)
            put("match", it.match.joinToString(separator = SEP))
            put("grant", it.grant.joinToString(separator = SEP))
            put("exclude", it.exclude.joinToString(separator = SEP))
            put("require", it.require.joinToString(separator = SEP))
          }
      if (db.insert("script", null, values).toString() == "-1") {
        Log.e("Insertion failed with: " + it.toString())
      }
    }
  }

  private fun query(
      selection: String?,
      selectionArgs: Array<String>?,
  ): Array<Script> {
    val db = dbHelper.readableDatabase
    val cursor = db.query("script", null, selection, selectionArgs, null, null, null)
    val scripts = mutableListOf<Script>()
    with(cursor) {
      while (moveToNext()) {
        val id = getString(getColumnIndexOrThrow("id"))
        val meta = getString(getColumnIndexOrThrow("meta"))
        val code = getString(getColumnIndexOrThrow("code"))
        val runAt = RunAt.valueOf(getString(getColumnIndexOrThrow("runAt")))
        val shouldWrap = getInt(getColumnIndexOrThrow("shouldWrap")) > 0
        val match = getString(getColumnIndexOrThrow("match")).split(SEP).toTypedArray()
        val grant = getString(getColumnIndexOrThrow("grant")).split(SEP).toTypedArray()
        val exclude = getString(getColumnIndexOrThrow("exclude")).split(SEP).toTypedArray()
        val require = getString(getColumnIndexOrThrow("require")).split(SEP).toTypedArray()
        val script = Script(id, match, grant, exclude, require, meta, code, runAt, shouldWrap)
        scripts.add(script)
      }
    }
    cursor.close()
    return scripts.toTypedArray()
  }

  fun getAll(): Array<Script> {
    return query(null, null)
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
    val SCRIPT_QUOTE_ESCAPE = "ChromeXt_Signle_Quote_Escape_String"
    var callback: String? = null
    when (action) {
      "installScript" -> {
        val script = parseScript(payload)
        if (script == null) {
          callback = "alert('Invalid UserScript')"
        } else {
          Log.i("Install script ${script.id}")
          insert(script)
        }
      }
      "getIds" -> {
        val result = getAll().map { it.id.replace("'", SCRIPT_QUOTE_ESCAPE) }
        callback =
            "window.dispatchEvent(new CustomEvent('script_id',{detail:['${result.joinToString(separator = "','")}']}));"
      }
      "getMetaById" -> {
        val ids = parseArray(payload)
        val result = query("id = ?", ids).map { it.meta.replace("`", SCRIPT_QUOTE_ESCAPE) }
        callback =
            "window.dispatchEvent(new CustomEvent('script_meta',{detail:[`${result.joinToString(separator = "`,`")}`]}));"
      }
      "updateMetaForId" -> {
        val CHROMEXT_SPLIT = "ChromeXt_Split_For_Metadata_Update"
        val result = payload.split(CHROMEXT_SPLIT)
        val match = query("id = ?", arrayOf(result[0]))
        if (match.size > 0) {
          val script = match.first()
          val newScript = parseScript(result[1] + "\n" + script.code)
          insert(newScript!!)
        }
      }
      "deleteScriptById" -> {
        val ids = parseArray(payload)
        delete(ids)
      }
    }
    return callback
  }
}
