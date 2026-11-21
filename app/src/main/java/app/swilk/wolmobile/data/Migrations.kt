package app.swilk.wolmobile.data

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

private const val CREATE_NETWORK = "CREATE TABLE IF NOT EXISTS `Network` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `probeAddress` TEXT, `probePort` INTEGER NOT NULL, `broadcastAddress` TEXT NOT NULL, `relayCommand` TEXT NOT NULL)"

/** Rows for a fresh database: hosts need a network, so there is always one to start with. */
suspend fun insertDefaultNetwork(connection: SQLiteConnection) {
    connection.execSQL("INSERT INTO `Network` (`id`, `name`, `probeAddress`, `probePort`, `broadcastAddress`, `relayCommand`) VALUES (1, 'Home', NULL, 22, '255.255.255.255', '$DEFAULT_RELAY_COMMAND')")
}

/** Home detection and the relay move from Settings, and the broadcast address from each host, into one "Home" network. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(CREATE_NETWORK)
        insertDefaultNetwork(connection)
        connection.execSQL(
            """
            UPDATE `Network` SET
                `probeAddress` = (SELECT `homeProbeAddress` FROM `Settings`),
                `probePort` = (SELECT `homeProbePort` FROM `Settings`),
                `relayCommand` = (SELECT `relayCommand` FROM `Settings`)
            WHERE EXISTS (SELECT 1 FROM `Settings`)
            """,
        )
        connection.execSQL(
            """
            UPDATE `Network` SET `broadcastAddress` =
                (SELECT `broadcastAddress` FROM `Host` GROUP BY `broadcastAddress` ORDER BY COUNT(*) DESC LIMIT 1)
            WHERE EXISTS (SELECT 1 FROM `Host`)
            """,
        )

        connection.execSQL("CREATE TABLE IF NOT EXISTS `NetworkRelay` (`networkId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `targetId` INTEGER NOT NULL, PRIMARY KEY(`networkId`, `position`), FOREIGN KEY(`networkId`) REFERENCES `Network`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`targetId`) REFERENCES `SshTarget`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_NetworkRelay_targetId` ON `NetworkRelay` (`targetId`)")
        connection.execSQL("INSERT INTO `NetworkRelay` (`networkId`, `position`, `targetId`) SELECT 1, 0, `relayTargetId` FROM `Settings` WHERE `relayTargetId` IS NOT NULL")

        connection.execSQL("CREATE TABLE IF NOT EXISTS `_new_Host` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `mac` TEXT NOT NULL, `networkId` INTEGER NOT NULL, `wolPort` INTEGER NOT NULL, `probeAddress` TEXT, `probePort` INTEGER, FOREIGN KEY(`networkId`) REFERENCES `Network`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
        connection.execSQL("INSERT INTO `_new_Host` (`id`, `name`, `mac`, `networkId`, `wolPort`, `probeAddress`, `probePort`) SELECT `id`, `name`, `mac`, 1, `wolPort`, `probeAddress`, `probePort` FROM `Host`")
        connection.execSQL("DROP TABLE `Host`")
        connection.execSQL("ALTER TABLE `_new_Host` RENAME TO `Host`")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_Host_networkId` ON `Host` (`networkId`)")

        connection.execSQL("CREATE TABLE IF NOT EXISTS `_new_Settings` (`id` INTEGER NOT NULL, `tile1` INTEGER, `tile2` INTEGER, `tile3` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`tile1`) REFERENCES `Action`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`tile2`) REFERENCES `Action`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`tile3`) REFERENCES `Action`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )")
        connection.execSQL("INSERT INTO `_new_Settings` (`id`, `tile1`, `tile2`, `tile3`) SELECT `id`, `tile1`, `tile2`, `tile3` FROM `Settings`")
        connection.execSQL("DROP TABLE `Settings`")
        connection.execSQL("ALTER TABLE `_new_Settings` RENAME TO `Settings`")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_Settings_tile1` ON `Settings` (`tile1`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_Settings_tile2` ON `Settings` (`tile2`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_Settings_tile3` ON `Settings` (`tile3`)")
    }
}
