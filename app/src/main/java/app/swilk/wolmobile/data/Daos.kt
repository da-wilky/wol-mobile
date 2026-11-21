package app.swilk.wolmobile.data

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.Query
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

    @Query("SELECT COUNT(*) FROM Action WHERE hostId = :id")
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

    @Query("SELECT name FROM SshTarget WHERE keyId = :id ORDER BY name")
    suspend fun targetNames(id: Long): List<String>

    @Upsert
    suspend fun upsert(key: SshKey)

    @Delete
    suspend fun delete(key: SshKey)
}

@Dao
interface SshTargetDao {
    @Query("SELECT * FROM SshTarget ORDER BY name COLLATE NOCASE")
    fun all(): Flow<List<SshTarget>>

    @Query("SELECT * FROM SshTarget WHERE id = :id")
    suspend fun get(id: Long): SshTarget?

    @Query("SELECT COUNT(*) FROM Action WHERE targetId = :id")
    suspend fun actionCount(id: Long): Int

    @Query("UPDATE SshTarget SET hostKey = :hostKey WHERE id = :id")
    suspend fun setHostKey(id: Long, hostKey: String?)

    @Upsert
    suspend fun upsert(target: SshTarget)

    @Delete
    suspend fun delete(target: SshTarget)
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

    @Query("SELECT targetId FROM NetworkRelay WHERE networkId = :id ORDER BY position")
    suspend fun relayIds(id: Long): List<Long>

    @Query("SELECT SshTarget.* FROM NetworkRelay JOIN SshTarget ON SshTarget.id = targetId WHERE networkId = :id ORDER BY position")
    suspend fun relays(id: Long): List<SshTarget>

    @Query("SELECT name FROM Host WHERE networkId = :id ORDER BY name COLLATE NOCASE")
    suspend fun hostNames(id: Long): List<String>

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

suspend fun NetworkDao.setRelays(networkId: Long, targetIds: List<Long>) {
    clearRelays(networkId)
    insertRelays(targetIds.mapIndexed { i, targetId -> NetworkRelay(networkId, i, targetId) })
}
