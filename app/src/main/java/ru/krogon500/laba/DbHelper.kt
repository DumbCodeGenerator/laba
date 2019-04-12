package ru.krogon500.laba

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper


class DbHelper(context: Context, dbName: String) : SQLiteOpenHelper(context, dbName, null, DATABASE_VERSION) {

    override fun onCreate(sqLiteDatabase: SQLiteDatabase) {
        sqLiteDatabase.execSQL(MARKERS_TABLE_CREATE_SQL)
        sqLiteDatabase.execSQL(IMAGES_TABLE_CREATE_SQL)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {

    }

    override fun onDowngrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {

    }

    @Throws(Exception::class)
    fun insertRow(table_name: String, values: ContentValues) {
        val rows = writableDatabase.insert(table_name, null, values)
        if (rows == -1L)
            throw Exception("Не вставилось")
    }

    @Throws(Exception::class)
    fun deleteRows(table_name: String, column: String, value: String) {
        val rows = writableDatabase.delete(table_name, "$column = ?", arrayOf(value))
        if (rows <= 0)
            throw Exception("Ни одна строка не удалилась")
    }

    fun selectMarkers(table_name: String): Cursor {
        return readableDatabase.rawQuery("SELECT * FROM $table_name", null)
    }

    fun selectImagesFromMarker(table_name: String, location: String): Cursor {
        return readableDatabase.rawQuery("SELECT * FROM $table_name WHERE $MARKER = '$location'", null)
    }

    companion object {
        const val DATABASE_NAME = "laba.db"
        private const val DATABASE_VERSION = 1
        const val MARKER = "marker"

        const val IMAGES_TABLE_NAME = "images"
        const val IMAGE = "image"
        internal const val IMAGES_TABLE_CREATE_SQL = "CREATE TABLE IF NOT EXISTS " + IMAGES_TABLE_NAME + " (" +
                IMAGE + " TEXT, " +
                MARKER + " TEXT);"

        const val MARKERS_TABLE_NAME = "markers"
        private const val ID = "id"
        internal const val MARKERS_TABLE_CREATE_SQL = "CREATE TABLE IF NOT EXISTS " + MARKERS_TABLE_NAME + " (" +
                ID + " INT AUTO_INCREMENT, " +
                MARKER + " TEXT);"
    }
}
