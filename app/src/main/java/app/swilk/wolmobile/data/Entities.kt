package app.swilk.wolmobile.data

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** A LAN: [probeAddress] answers only from inside it; away from it, [relays][NetworkRelay] send the magic packet. */
@Entity
data class Network(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val probeAddress: String?,
    val probePort: Int,
    val broadcastAddress: String,
    val relayCommand: String,
    /** If set, presence also requires the probe to present this SSH host key, so another LAN with the same address doesn't count. */
    @ColumnInfo(defaultValue = "NULL") val probeHostKey: String? = null,
)

@Entity(
    foreignKeys = [ForeignKey(Network::class, ["id"], ["networkId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("networkId")],
)
data class Host(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mac: String,
    val networkId: Long,
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
    foreignKeys = [ForeignKey(SshKey::class, ["id"], ["keyId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("keyId")],
)
data class SshTarget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val address: String,
    val port: Int,
    val user: String,
    val keyId: Long?,
    val hostKey: String?,
)

/** Relays of a network, tried in [position] order. */
@Entity(
    primaryKeys = ["networkId", "position"],
    foreignKeys = [
        ForeignKey(Network::class, ["id"], ["networkId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(SshTarget::class, ["id"], ["targetId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("targetId")],
)
data class NetworkRelay(val networkId: Long, val position: Int, val targetId: Long)

enum class ActionType { WAKE, SSH }

/** For SSH actions, [hostId] is optional and names the host whose status the command changes. Wake actions are deleted with their host by [HostDao.deleteWithWakeActions]. */
@Entity(
    foreignKeys = [
        ForeignKey(Host::class, ["id"], ["hostId"], onDelete = ForeignKey.SET_NULL),
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
    @ColumnInfo(defaultValue = "0") val requireUnlock: Boolean = false,
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
) {
    /** Runs through [app.swilk.wolmobile.ui.RunActionActivity] to confirm or unlock first. */
    val interactive: Boolean get() = confirmBeforeRun || requireUnlock
}

const val TILE_COUNT = 3
const val DEFAULT_RELAY_COMMAND = "wakeonlan {mac}"

@Entity(
    foreignKeys = [
        ForeignKey(Action::class, ["id"], ["tile1"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(Action::class, ["id"], ["tile2"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(Action::class, ["id"], ["tile3"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("tile1"), Index("tile2"), Index("tile3")],
)
data class Settings(
    @PrimaryKey val id: Int = 0,
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
