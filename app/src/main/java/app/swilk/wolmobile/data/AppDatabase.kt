package app.swilk.wolmobile.data

import android.content.Context
import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver

@Database(
    entities = [
        Network::class, NetworkRelay::class, Host::class, SshKey::class, SshTarget::class, Action::class, Settings::class,
        ActionState::class, RunLog::class, HostStatus::class, WidgetConfig::class,
    ],
    version = 5,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun networks(): NetworkDao
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
                .addMigrations(MIGRATION_2_3)
                .addCallback(
                    object : Callback() {
                        override suspend fun onCreate(connection: SQLiteConnection) = insertDefaultNetwork(connection)
                    },
                )
                .build()
    }
}
