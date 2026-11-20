package app.swilk.wolmobile.data

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Query
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

    @Query("SELECT COUNT(*) FROM SshTarget WHERE keyId = :id")
    suspend fun targetCount(id: Long): Int

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
    @Query("SELECT * FROM Action ORDER BY label COLLATE NOCASE")
    fun all(): Flow<List<Action>>

    @Query("SELECT * FROM Action ORDER BY label COLLATE NOCASE")
    suspend fun list(): List<Action>

    @Query("SELECT * FROM Action WHERE id = :id")
    suspend fun get(id: Long): Action?

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
