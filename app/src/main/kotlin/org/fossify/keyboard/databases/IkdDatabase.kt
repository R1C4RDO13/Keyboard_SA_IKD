package org.fossify.keyboard.databases

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import org.fossify.keyboard.interfaces.IkdEventDao
import org.fossify.keyboard.interfaces.SensorSampleDao
import org.fossify.keyboard.interfaces.SessionDao
import org.fossify.keyboard.models.IkdEvent
import org.fossify.keyboard.models.SensorSample
import org.fossify.keyboard.models.SessionRecord

@Database(entities = [SessionRecord::class, IkdEvent::class, SensorSample::class], version = 1)
abstract class IkdDatabase : RoomDatabase() {

    abstract fun SessionDao(): SessionDao

    abstract fun IkdEventDao(): IkdEventDao

    abstract fun SensorSampleDao(): SensorSampleDao

    companion object {
        private var db: IkdDatabase? = null

        fun getInstance(context: Context): IkdDatabase {
            if (db == null) {
                synchronized(IkdDatabase::class) {
                    if (db == null) {
                        db = Room.databaseBuilder(context, IkdDatabase::class.java, "ikd.db").build()
                        db!!.openHelper.setWriteAheadLoggingEnabled(true)
                    }
                }
            }
            return db!!
        }

        fun destroyInstance() {
            db = null
        }
    }
}
