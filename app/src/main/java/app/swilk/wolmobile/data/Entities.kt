package app.swilk.wolmobile.data

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity
data class Host(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mac: String,
    val broadcastAddress: String,
    val wolPort: Int,
    val probeAddress: String?,
    val probePort: Int?,
)

@Entity
class SshKey(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val publicKey: String,
    val encryptedPrivateKey: ByteArray,
)

@Entity(
    foreignKeys = [ForeignKey(SshKey::class, ["id"], ["keyId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("keyId")],
)
data class SshTarget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val address: String,
    val port: Int,
    val user: String,
    val keyId: Long,
    val hostKey: String?,
)

enum class ActionType { WAKE, SSH }

/** For SSH actions, [hostId] is optional and names the host whose status the command changes. */
@Entity(
    foreignKeys = [
        ForeignKey(Host::class, ["id"], ["hostId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(SshTarget::class, ["id"], ["targetId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("hostId"), Index("targetId")],
)
data class Action(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val type: ActionType,
    val hostId: Long?,
    val targetId: Long?,
    val command: String?,
    val confirmBeforeRun: Boolean,
    val shortcut: Boolean,
)

const val TILE_COUNT = 3
const val DEFAULT_RELAY_COMMAND = "wakeonlan {mac}"

@Entity(
    foreignKeys = [
        ForeignKey(SshTarget::class, ["id"], ["relayTargetId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(Action::class, ["id"], ["tile1"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(Action::class, ["id"], ["tile2"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(Action::class, ["id"], ["tile3"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("relayTargetId"), Index("tile1"), Index("tile2"), Index("tile3")],
)
data class Settings(
    @PrimaryKey val id: Int = 0,
    val homeProbeAddress: String? = null,
    val homeProbePort: Int = 22,
    val relayTargetId: Long? = null,
    val relayCommand: String = DEFAULT_RELAY_COMMAND,
    val tile1: Long? = null,
    val tile2: Long? = null,
    val tile3: Long? = null,
) {
    val tiles get() = listOf(tile1, tile2, tile3)

    fun withTile(slot: Int, actionId: Long?) = when (slot) {
        0 -> copy(tile1 = actionId)
        1 -> copy(tile2 = actionId)
        else -> copy(tile3 = actionId)
    }
}

enum class RunState { IDLE, RUNNING, OK, FAILED }

@Entity(foreignKeys = [ForeignKey(Action::class, ["id"], ["actionId"], onDelete = ForeignKey.CASCADE)])
data class ActionState(
    @PrimaryKey val actionId: Long,
    val state: RunState,
)

@Entity
data class RunLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val at: Long,
    val ok: Boolean,
    val message: String,
    val output: String,
)

@Entity(foreignKeys = [ForeignKey(Host::class, ["id"], ["hostId"], onDelete = ForeignKey.CASCADE)])
data class HostStatus(
    @PrimaryKey val hostId: Long,
    val up: Boolean,
    val checkedAt: Long,
)

/** [actionIds] is a comma-separated, ordered list. */
@Entity
data class WidgetConfig(
    @PrimaryKey val appWidgetId: Int,
    val actionIds: String,
    val showStatus: Boolean,
) {
    val actionIdList get() = actionIds.split(',').mapNotNull { it.toLongOrNull() }
}
