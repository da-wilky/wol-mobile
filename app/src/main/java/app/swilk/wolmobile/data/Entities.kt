package app.swilk.wolmobile.data

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Relation
import kotlinx.serialization.Serializable

/** A LAN. You're on it when [presenceHostId]'s local address answers over Wi-Fi; away from it, [relays][NetworkRelay] send the magic packet. */
@Serializable
@Entity(
    foreignKeys = [ForeignKey(Host::class, ["id"], ["presenceHostId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("presenceHostId")],
)
data class Network(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Null: always counts as on this network. */
    val presenceHostId: Long?,
    val broadcastAddress: String,
    val relayCommand: String,
)

/**
 * A machine. On its network it's reached at [localAddress] over Wi-Fi, elsewhere at [vpnAddress].
 * [hostKey] is its pinned SSH host key; presence checks and every login verify it.
 */
@Serializable
@Entity(
    foreignKeys = [ForeignKey(Network::class, ["id"], ["networkId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("networkId")],
)
data class Host(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val networkId: Long?,
    val mac: String?,
    val wolPort: Int,
    val localAddress: String?,
    val vpnAddress: String?,
    val sshPort: Int,
    val hostKey: String?,
    val checkStatus: Boolean,
    /** Null: ping instead. */
    val statusPort: Int?,
)

@Entity
class SshKey(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val publicKey: String,
    val encryptedPrivateKey: ByteArray,
)

@Serializable
@Entity(
    foreignKeys = [
        ForeignKey(Host::class, ["id"], ["hostId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(SshKey::class, ["id"], ["keyId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("hostId"), Index("keyId")],
)
data class SshLogin(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hostId: Long,
    val name: String,
    val user: String,
    val keyId: Long?,
)

/** A login with its host, as relays and SSH actions use it. */
data class Login(
    @Embedded val login: SshLogin,
    @Relation(parentColumns = ["hostId"], entityColumns = ["id"]) val host: Host,
) {
    val label get() = "${host.name} · ${login.name}"
}

/** Relays of a network, tried in [position] order. */
@Serializable
@Entity(
    primaryKeys = ["networkId", "position"],
    foreignKeys = [
        ForeignKey(Network::class, ["id"], ["networkId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(SshLogin::class, ["id"], ["loginId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("loginId")],
)
data class NetworkRelay(val networkId: Long, val position: Int, val loginId: Long)


enum class ActionType { WAKE, SSH }

/** For SSH actions, [hostId] is the login's host when the command changes its status, e.g. poweroff, and null otherwise. */
@Serializable
@Entity(
    foreignKeys = [
        ForeignKey(Host::class, ["id"], ["hostId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(SshLogin::class, ["id"], ["loginId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("hostId"), Index("loginId")],
)
data class Action(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val type: ActionType,
    val hostId: Long?,
    val loginId: Long?,
    val command: String?,
    val confirmBeforeRun: Boolean,
    val shortcut: Boolean,
    val requireUnlock: Boolean = false,
    val position: Int = 0,
) {
    /** Runs through [app.swilk.wolmobile.ui.RunActionActivity] to confirm or unlock first. */
    val interactive: Boolean get() = confirmBeforeRun || requireUnlock
}

const val TILE_COUNT = 3
const val DEFAULT_RELAY_COMMAND = "wakeonlan {mac}"

@Serializable
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
