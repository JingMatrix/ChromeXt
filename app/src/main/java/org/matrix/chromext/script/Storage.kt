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

@Entity
data class Script(
    @PrimaryKey val id: String,
    val match: Array<String>,
    val grant: Array<String>,
    val exclude: Array<String>,
    val require: Array<String>,
    var code: String,
    val runAt: RunAt,
    var encoded: Boolean
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

  // @Query("SELECT * FROM script WHERE id IN (:scriptIds)")
  // fun getScriptById(scriptIds: List<String>): List<Script>

  @Delete fun delete(script: Script): Int
}

@Database(entities = [Script::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
  abstract fun init(): ScriptDao
}
