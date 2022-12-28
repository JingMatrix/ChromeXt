package org.matrix.chromext.script

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity
data class Script(
    @PrimaryKey val id: String,
    val match: Array<String>,
    val grant: Array<String>,
    val exclude: Array<String>,
    val require: Array<String>,
    var meta: String,
    var code: String,
    val runAt: RunAt,
    val shouldWrap: Boolean = false
)

class Converters {
  @TypeConverter
  fun joinString(array: Array<String>): String {
    return array.joinToString(separator = "")
  }

  @TypeConverter
  fun spiltString(str: String): Array<String> {
    return str.split("").toTypedArray()
  }
}

@Dao
interface ScriptDao {
  @Query("SELECT * FROM script") fun getAll(): List<Script>

  @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertAll(vararg script: Script)

  // For front-end
  // @Query("SELECT * FROM script WHERE runAt is (:runAt)")
  // fun getIdByRunAt(runAt: List<RunAt>): List<Script>

  @Query("SELECT * FROM script WHERE id IN (:scriptIds)")
  fun getScriptById(scriptIds: List<String>): List<Script>

  @Delete fun delete(script: Script): Int
}

val MIGRATION_2_3 =
    object : Migration(2, 3) {
      override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE script ADD COLUMN meta TEXT NOT NULL DEFAULT ''")
      }
    }

val MIGRATION_3_4 =
    object : Migration(3, 4) {
      override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE script RENAME COLUMN encoded to shouldWrap")
        database.execSQL("UPDATE script SET shouldWrap = false")
      }
    }

@Database(entities = [Script::class], version = 4, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
  abstract fun init(): ScriptDao
}
