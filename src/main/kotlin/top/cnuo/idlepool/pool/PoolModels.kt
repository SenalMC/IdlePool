package top.cnuo.idlepool.pool

import org.bukkit.Location
import java.time.Duration

data class PoolVisuals(val startItem: String, val infoItem: String, val rewardsItem: String) {
    fun startItem() = startItem
    fun infoItem() = infoItem
    fun rewardsItem() = rewardsItem
}

class CuboidRegion(
    val world: String,
    x1: Int,
    y1: Int,
    z1: Int,
    x2: Int,
    y2: Int,
    z2: Int,
) {
    val minX = minOf(x1, x2)
    val minY = minOf(y1, y2)
    val minZ = minOf(z1, z2)
    val maxX = maxOf(x1, x2)
    val maxY = maxOf(y1, y2)
    val maxZ = maxOf(z1, z2)

    fun contains(location: Location): Boolean = location.world?.name == world &&
        location.blockX in minX..maxX && location.blockY in minY..maxY && location.blockZ in minZ..maxZ

    fun world() = world
    fun minX() = minX
    fun minY() = minY
    fun minZ() = minZ
    fun maxX() = maxX
    fun maxY() = maxY
    fun maxZ() = maxZ
}

data class PoolDefinition(
    val id: String,
    val enabled: Boolean,
    val displayName: String,
    val region: CuboidRegion,
    val permission: String,
    val maxActivePlayers: Int,
    val rewardCycle: Duration,
    val progressRetention: Duration,
    val rewardPlan: String,
    val visuals: PoolVisuals,
) {
    fun id() = id
    fun enabled() = enabled
    fun displayName() = displayName
    fun region() = region
    fun permission() = permission
    fun maxActivePlayers() = maxActivePlayers
    fun rewardCycle() = rewardCycle
    fun progressRetention() = progressRetention
    fun rewardPlan() = rewardPlan
    fun visuals() = visuals
}
