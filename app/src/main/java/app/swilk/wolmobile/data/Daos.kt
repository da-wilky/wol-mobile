package app.swilk.wolmobile.data

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HostDao {
    @Query("SELECT * FROM Host ORDER BY name COLLATE NOCASE")
    fun all(): Flow<List<Host>>

    @Query("SELECT * FROM Host ORDER BY name COLLATE NOCASE")
    suspend fun list(): List<Host>

    @Query("SELECT * FROM Host WHERE id = :id")
    suspend fun get(id: Long): Host?

    @Query("SELECT * FROM Host WHERE networkId = :id ORDER BY name COLLATE NOCASE")
    suspend fun onNetwork(id: Long): List<Host>

    @Query(
        "SELECT (SELECT COUNT(*) FROM Action WHERE hostId = :id OR loginId IN (SELECT id FROM SshLogin WHERE hostId = :id)) + " +
            "(SELECT COUNT(*) FROM NetworkRelay WHERE loginId IN (SELECT id FROM SshLogin WHERE hostId = :id))",
    )
    suspend fun actionCount(id: Long): Int

    @Upsert
    suspend fun upsert(host: Host)

    @Delete
    suspend fun delete(host: Host)
}

@Dao
interface SshKeyDao {
    @Query("SELECT * FROM SshKey ORDER BY name COLLATE NOCASE")
    fun all(): Flow<List<SshKey>>

    @Query("SELECT * FROM SshKey WHERE id = :id")
    suspend fun get(id: Long): SshKey?

    @Query("SELECT Host.name || ' · ' || SshLogin.name FROM SshLogin JOIN Host ON Host.id = hostId WHERE keyId = :id ORDER BY 1")
    suspend fun loginNames(id: Long): List<String>

    @Upsert
    suspend fun upsert(key: SshKey)

    @Delete
    suspend fun delete(key: SshKey)
}

@Dao
interface LoginDao {
    @Transaction
    @Query("SELECT SshLogin.* FROM SshLogin JOIN Host ON Host.id = hostId ORDER BY Host.name COLLATE NOCASE, SshLogin.name COLLATE NOCASE")
    fun all(): Flow<List<Login>>

    @Transaction
    @Query("SELECT * FROM SshLogin WHERE id = :id")
    suspend fun get(id: Long): Login?

    @Query("SELECT * FROM SshLogin WHERE hostId = :id ORDER BY name COLLATE NOCASE")
    fun forHost(id: Long): Flow<List<SshLogin>>

    @Query("SELECT (SELECT COUNT(*) FROM Action WHERE loginId = :id) + (SELECT COUNT(*) FROM NetworkRelay WHERE loginId = :id)")
    suspend fun usageCount(id: Long): Int

    @Upsert
    suspend fun upsert(login: SshLogin)

    @Delete
    suspend fun delete(login: SshLogin)
}

@Dao
interface ActionDao {
    @Query("SELECT * FROM Action ORDER BY position, label COLLATE NOCASE")
    fun all(): Flow<List<Action>>

    @Query("SELECT * FROM Action ORDER BY position, label COLLATE NOCASE")
    suspend fun list(): List<Action>

    @Query("SELECT * FROM Action WHERE id = :id")
    suspend fun get(id: Long): Action?

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM Action")
    suspend fun nextPosition(): Int

    @Update
    suspend fun update(actions: List<Action>)

    @Upsert
    suspend fun upsert(action: Action)

    @Delete
    suspend fun delete(action: Action)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM Settings WHERE id = 0")
    fun flow(): Flow<Settings?>

    @Query("SELECT * FROM Settings WHERE id = 0")
    suspend fun getOrNull(): Settings?

    @Upsert
    suspend fun upsert(settings: Settings)
}

suspend fun SettingsDao.get() = getOrNull() ?: Settings()

@Dao
interface RunDao {
    @Query("SELECT * FROM ActionState")
    fun states(): Flow<List<ActionState>>

    @Upsert
    suspend fun setState(state: ActionState)

    @Query("UPDATE ActionState SET state = 'IDLE' WHERE actionId = :id AND state IN ('OK', 'FAILED')")
    suspend fun clearResult(id: Long)

    @Query("SELECT * FROM RunLog ORDER BY id DESC")
    fun log(): Flow<List<RunLog>>

    @Upsert
    suspend fun insertLog(entry: RunLog)

    @Query("DELETE FROM RunLog")
    suspend fun clearLog()

    @Query("DELETE FROM RunLog WHERE id NOT IN (SELECT id FROM RunLog ORDER BY id DESC LIMIT :keep)")
    suspend fun trimLog(keep: Int)
}

@Dao
interface StatusDao {
    @Query("SELECT * FROM HostStatus")
    fun all(): Flow<List<HostStatus>>

    @Upsert
    suspend fun set(status: HostStatus)
}

@Dao
interface WidgetDao {
    @Query("SELECT * FROM WidgetConfig")
    fun all(): Flow<List<WidgetConfig>>

    @Query("SELECT * FROM WidgetConfig WHERE appWidgetId = :id")
    fun flow(id: Int): Flow<WidgetConfig?>

    @Query("SELECT * FROM WidgetConfig WHERE appWidgetId = :id")
    suspend fun get(id: Int): WidgetConfig?

    @Query("SELECT * FROM WidgetConfig")
    suspend fun list(): List<WidgetConfig>

    @Upsert
    suspend fun upsert(config: WidgetConfig)

    @Query("DELETE FROM WidgetConfig WHERE appWidgetId IN (:ids)")
    suspend fun delete(ids: List<Int>)
}

@Dao
interface NetworkDao {
    @Query("SELECT * FROM Network ORDER BY name COLLATE NOCASE")
    fun all(): Flow<List<Network>>

    @Query("SELECT * FROM Network WHERE id = :id")
    suspend fun get(id: Long): Network?

    @Query("SELECT * FROM NetworkRelay ORDER BY networkId, position")
    fun allRelays(): Flow<List<NetworkRelay>>

    @Query("SELECT loginId FROM NetworkRelay WHERE networkId = :id ORDER BY position")
    suspend fun relayIds(id: Long): List<Long>

    @Transaction
    @Query("SELECT SshLogin.* FROM NetworkRelay JOIN SshLogin ON SshLogin.id = loginId WHERE networkId = :id ORDER BY position")
    suspend fun relays(id: Long): List<Login>

    /** The new row's id, or -1 when an existing network was updated. */
    @Upsert
    suspend fun upsert(network: Network): Long

    @Query("DELETE FROM NetworkRelay WHERE networkId = :id")
    suspend fun clearRelays(id: Long)

    @Insert
    suspend fun insertRelays(relays: List<NetworkRelay>)

    @Delete
    suspend fun delete(network: Network)
}

suspend fun NetworkDao.setRelays(networkId: Long, loginIds: List<Long>) {
    clearRelays(networkId)
    insertRelays(loginIds.mapIndexed { i, loginId -> NetworkRelay(networkId, i, loginId) })
}

@Dao
interface TransferDao {
    @Query("SELECT * FROM Network")
    suspend fun networks(): List<Network>

    @Query("SELECT * FROM Host")
    suspend fun hosts(): List<Host>

    @Query("SELECT * FROM SshKey")
    suspend fun keys(): List<SshKey>

    @Query("SELECT * FROM SshLogin")
    suspend fun logins(): List<SshLogin>

    @Query("SELECT * FROM NetworkRelay")
    suspend fun relays(): List<NetworkRelay>

    @Query("SELECT * FROM Action")
    suspend fun actions(): List<Action>

    @Query("SELECT * FROM Settings WHERE id = 0")
    suspend fun settings(): Settings?

    @Query("DELETE FROM Settings")
    suspend fun clearSettings()

    @Query("DELETE FROM WidgetConfig")
    suspend fun clearWidgets()

    @Query("DELETE FROM Action")
    suspend fun clearActions()

    @Query("DELETE FROM NetworkRelay")
    suspend fun clearRelays()

    @Query("DELETE FROM SshLogin")
    suspend fun clearLogins()

    @Query("UPDATE Network SET presenceHostId = NULL")
    suspend fun clearPresence()

    @Query("DELETE FROM Host")
    suspend fun clearHosts()

    @Query("DELETE FROM Network")
    suspend fun clearNetworks()

    @Query("DELETE FROM SshKey")
    suspend fun clearKeys()

    @Insert
    suspend fun insertNetworks(networks: List<Network>)

    @Update
    suspend fun updateNetworks(networks: List<Network>)

    @Insert
    suspend fun insertHosts(hosts: List<Host>)

    @Insert
    suspend fun insertKeys(keys: List<SshKey>)

    @Insert
    suspend fun insertLogins(logins: List<SshLogin>)

    @Insert
    suspend fun insertRelays(relays: List<NetworkRelay>)

    @Insert
    suspend fun insertActions(actions: List<Action>)

    @Insert
    suspend fun insertSettings(settings: Settings)

    /** Networks and hosts reference each other, so presence is linked once both exist. */
    @Transaction
    suspend fun replaceAll(
        networks: List<Network>, hosts: List<Host>, keys: List<SshKey>, logins: List<SshLogin>,
        relays: List<NetworkRelay>, actions: List<Action>, settings: Settings?,
    ) {
        clearSettings(); clearWidgets(); clearActions(); clearRelays(); clearLogins()
        clearPresence(); clearHosts(); clearNetworks(); clearKeys()
        insertNetworks(networks.map { it.copy(presenceHostId = null) })
        insertHosts(hosts)
        updateNetworks(networks)
        insertKeys(keys)
        insertLogins(logins)
        insertRelays(relays)
        insertActions(actions)
        settings?.let { insertSettings(it) }
    }
}
