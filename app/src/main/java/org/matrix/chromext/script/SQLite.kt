package org.matrix.chromext.script

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

enum class RunAt(val state: String) {
  START("document-start"),
  END("document-end"),
  IDLE("document-idle")
}

data class Script(
    val id: String,
    val match: Array<String>,
    val grant: Array<String>,
    val exclude: Array<String>,
    val require: Array<String>,
    val resource: Array<String>,
    var meta: String,
    val code: String,
    val runAt: RunAt,
    var storage: String
)

private const val SQL_CREATE_ENTRIES =
    "CREATE TABLE script (id TEXT PRIMARY KEY NOT NULL, match TEXT NOT NULL, grant TEXT NOT NULL, exclude TEXT NOT NULL, require TEXT NOT NULL, resource TEXT NOT NULL, meta TEXT NOT NULL, code TEXT NOT NULL, runAt TEXT NOT NULL, storage TEXT NOT NULL);"

class ScriptDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL(SQL_CREATE_ENTRIES)
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (newVersion < 7) {
      db.execSQL("DROP TABLE script;")
      onCreate(db)
    }
    if (oldVersion == 6 && newVersion == 7) {
      db.execSQL("ALTER TABLE script ADD storage TEXT NOT NULL DEFAULT '';")
    }
  }

  override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

  companion object {
    const val DATABASE_VERSION = 7
    const val DATABASE_NAME = "userscript"
  }
}
