package top.cnuo.idlepool.pool;

import org.bukkit.Location;

public record CuboidRegion(
        String world,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
) {
    public CuboidRegion {
        int actualMinX = Math.min(minX, maxX);
        int actualMinY = Math.min(minY, maxY);
        int actualMinZ = Math.min(minZ, maxZ);
        int actualMaxX = Math.max(minX, maxX);
        int actualMaxY = Math.max(minY, maxY);
        int actualMaxZ = Math.max(minZ, maxZ);
        minX = actualMinX;
        minY = actualMinY;
        minZ = actualMinZ;
        maxX = actualMaxX;
        maxY = actualMaxY;
        maxZ = actualMaxZ;
    }

    public boolean contains(Location location) {
        if (location.getWorld() == null || !location.getWorld().getName().equals(world)) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }
}
