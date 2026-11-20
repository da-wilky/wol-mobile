package app.swilk.wolmobile.data

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver

@Database(
    entities = [
        Host::class, SshKey::class, SshTarget::class, Action::class, Settings::class,
        ActionState::class, RunLog::class, HostStatus::class, WidgetConfig::class,
    ],
    version = 1,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun hosts(): HostDao
    abstract fun keys(): SshKeyDao
    abstract fun targets(): SshTargetDao
    abstract fun actions(): ActionDao
    abstract fun settings(): SettingsDao
    abstract fun runs(): RunDao
    abstract fun status(): StatusDao
    abstract fun widgets(): WidgetDao

    companion object {
        fun open(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "wol.db")
                .setDriver(AndroidSQLiteDriver())
                .build()
    }
}
