package top.cnuo.idlepool.storage;

import java.time.Instant;

public record InboxEntry(
        long id,
        String poolId,
        String provider,
        String itemId,
        int amount,
        String displayName,
        Instant createdAt
) {
}
