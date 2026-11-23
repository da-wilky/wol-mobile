package app.swilk.wolmobile.data

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Rows for a fresh database. */
suspend fun insertDefaultNetwork(connection: SQLiteConnection) {
    connection.execSQL("INSERT INTO `Network` (`id`, `name`, `presenceHostId`, `broadcastAddress`, `relayCommand`) VALUES (1, 'Home', NULL, '255.255.255.255', '$DEFAULT_RELAY_COMMAND')")
}

// Copied from schemas/6.json, without the kept SshKey and RunLog.
private val CREATE_V6 = listOf(
    "CREATE TABLE IF NOT EXISTS `Network` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `presenceHostId` INTEGER, `broadcastAddress` TEXT NOT NULL, `relayCommand` TEXT NOT NULL, FOREIGN KEY(`presenceHostId`) REFERENCES `Host`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
    "CREATE INDEX IF NOT EXISTS `index_Network_presenceHostId` ON `Network` (`presenceHostId`)",
    "CREATE TABLE IF NOT EXISTS `NetworkRelay` (`networkId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `loginId` INTEGER NOT NULL, PRIMARY KEY(`networkId`, `position`), FOREIGN KEY(`networkId`) REFERENCES `Network`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`loginId`) REFERENCES `SshLogin`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_NetworkRelay_loginId` ON `NetworkRelay` (`loginId`)",
    "CREATE TABLE IF NOT EXISTS `Host` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `networkId` INTEGER, `mac` TEXT, `wolPort` INTEGER NOT NULL, `localAddress` TEXT, `vpnAddress` TEXT, `sshPort` INTEGER NOT NULL, `hostKey` TEXT, `checkStatus` INTEGER NOT NULL, `statusPort` INTEGER, FOREIGN KEY(`networkId`) REFERENCES `Network`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
    "CREATE INDEX IF NOT EXISTS `index_Host_networkId` ON `Host` (`networkId`)",
    "CREATE TABLE IF NOT EXISTS `SshLogin` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `hostId` INTEGER NOT NULL, `name` TEXT NOT NULL, `user` TEXT NOT NULL, `keyId` INTEGER, FOREIGN KEY(`hostId`) REFERENCES `Host`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`keyId`) REFERENCES `SshKey`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
    "CREATE INDEX IF NOT EXISTS `index_SshLogin_hostId` ON `SshLogin` (`hostId`)",
    "CREATE INDEX IF NOT EXISTS `index_SshLogin_keyId` ON `SshLogin` (`keyId`)",
    "CREATE TABLE IF NOT EXISTS `Action` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `label` TEXT NOT NULL, `type` TEXT NOT NULL, `hostId` INTEGER, `loginId` INTEGER, `command` TEXT, `confirmBeforeRun` INTEGER NOT NULL, `shortcut` INTEGER NOT NULL, `requireUnlock` INTEGER NOT NULL, `position` INTEGER NOT NULL, FOREIGN KEY(`hostId`) REFERENCES `Host`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`loginId`) REFERENCES `SshLogin`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE INDEX IF NOT EXISTS `index_Action_hostId` ON `Action` (`hostId`)",
    "CREATE INDEX IF NOT EXISTS `index_Action_loginId` ON `Action` (`loginId`)",
    "CREATE TABLE IF NOT EXISTS `Settings` (`id` INTEGER NOT NULL, `tile1` INTEGER, `tile2` INTEGER, `tile3` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`tile1`) REFERENCES `Action`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`tile2`) REFERENCES `Action`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`tile3`) REFERENCES `Action`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
    "CREATE INDEX IF NOT EXISTS `index_Settings_tile1` ON `Settings` (`tile1`)",
    "CREATE INDEX IF NOT EXISTS `index_Settings_tile2` ON `Settings` (`tile2`)",
    "CREATE INDEX IF NOT EXISTS `index_Settings_tile3` ON `Settings` (`tile3`)",
    "CREATE TABLE IF NOT EXISTS `ActionState` (`actionId` INTEGER NOT NULL, `state` TEXT NOT NULL, PRIMARY KEY(`actionId`), FOREIGN KEY(`actionId`) REFERENCES `Action`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE TABLE IF NOT EXISTS `HostStatus` (`hostId` INTEGER NOT NULL, `up` INTEGER NOT NULL, `checkedAt` INTEGER NOT NULL, PRIMARY KEY(`hostId`), FOREIGN KEY(`hostId`) REFERENCES `Host`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE TABLE IF NOT EXISTS `WidgetConfig` (`appWidgetId` INTEGER NOT NULL, `actionIds` TEXT NOT NULL, `showStatus` INTEGER NOT NULL, PRIMARY KEY(`appWidgetId`))",
)

// Referencing tables first, so dropping never trips a foreign key.
private val OLD_TABLES = listOf("WidgetConfig", "HostStatus", "ActionState", "Settings", "NetworkRelay", "Action", "SshTarget", "Host", "Network")

/** Version 6 regrouped everything around hosts; only keys and the log carry over, the rest is set up again. */
val REBUILD_MIGRATIONS = (1..5).map { from ->
    object : Migration(from, 6) {
        override suspend fun migrate(connection: SQLiteConnection) {
            OLD_TABLES.forEach { connection.execSQL("DROP TABLE IF EXISTS `$it`") }
            CREATE_V6.forEach { connection.execSQL(it) }
            insertDefaultNetwork(connection)
        }
    }
}.toTypedArray()
