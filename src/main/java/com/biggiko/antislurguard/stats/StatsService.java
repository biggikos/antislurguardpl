package com.biggiko.antislurguard.stats;

import java.util.concurrent.atomic.AtomicInteger;

public final class StatsService {

    private final AtomicInteger nameBlocks = new AtomicInteger();
    private final AtomicInteger chatBlocks = new AtomicInteger();

    public void incrementNameBlocks() {
        nameBlocks.incrementAndGet();
    }

    public void incrementChatBlocks() {
        chatBlocks.incrementAndGet();
    }

    public Snapshot drain() {
        int names = nameBlocks.getAndSet(0);
        int chats = chatBlocks.getAndSet(0);
        return new Snapshot(names, chats);
    }

    public int currentNameBlocks() {
        return nameBlocks.get();
    }

    public int currentChatBlocks() {
        return chatBlocks.get();
    }

    public record Snapshot(int nameBlocks, int chatBlocks) {
        public boolean shouldAlert(int threshold) {
            if (threshold <= 0) {
                return nameBlocks > 0 || chatBlocks > 0;
            }
            return (nameBlocks + chatBlocks) >= threshold;
        }
    }
}
