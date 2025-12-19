package com.biggiko.antislurguard.antispam;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.biggiko.antislurguard.config.Config;

public final class AntiSpamService {

    private final Config.AntiSpamSettings settings;
    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();

    public AntiSpamService(Config.AntiSpamSettings settings) {
        this.settings = settings;
    }

    public CheckResult evaluate(UUID uuid, String normalizedMessage, long nowMillis) {
        if (!settings.enabled() || uuid == null) {
            return CheckResult.allow();
        }
        String normalized = normalizedMessage == null ? "" : normalizedMessage;
        PlayerState state = states.computeIfAbsent(uuid, id -> new PlayerState());
        synchronized (state) {
            if (state.slowmodeUntil > 0 && nowMillis >= state.slowmodeUntil) {
                state.slowmodeUntil = 0L;
            }
            long cooldownMillis = Math.max(0L, settings.slowmodeCooldownSeconds() * 1000L);
            if (state.slowmodeUntil > nowMillis && cooldownMillis > 0 && state.lastAllowedAt > 0L) {
                long sinceLastAllowed = nowMillis - state.lastAllowedAt;
                if (sinceLastAllowed < cooldownMillis) {
                    long remainingMillis = cooldownMillis - sinceLastAllowed;
                    long remainingSeconds = Math.max(1L, (remainingMillis + 999) / 1000);
                    state.lastMessageAt = nowMillis;
                    state.lastNormalizedMessage = normalized;
                    return CheckResult.cooldown(remainingSeconds);
                }
            }
            long repeatWindowMillis = Math.max(0L, settings.repeatWindowSeconds() * 1000L);
            if (state.lastNormalizedMessage != null
                    && state.lastNormalizedMessage.equals(normalized)
                    && (nowMillis - state.lastMessageAt) <= repeatWindowMillis) {
                state.repeatCount++;
            } else {
                state.repeatCount = 1;
            }
            state.lastMessageAt = nowMillis;
            state.lastNormalizedMessage = normalized;

            int threshold = Math.max(1, settings.repeatThreshold());
            long slowmodeDurationMillis = Math.max(0L, settings.slowmodeDurationSeconds() * 1000L);
            if (slowmodeDurationMillis > 0L && state.repeatCount >= threshold) {
                state.repeatCount = 0;
                state.slowmodeUntil = nowMillis + slowmodeDurationMillis;
                state.lastAllowedAt = 0L;
                long durationSeconds = Math.max(1L, settings.slowmodeDurationSeconds());
                long cooldownSeconds = Math.max(1L, settings.slowmodeCooldownSeconds());
                return CheckResult.triggered(durationSeconds, cooldownSeconds);
            }

            state.lastAllowedAt = nowMillis;
            return CheckResult.allow();
        }
    }

    public enum Reason {
        NONE,
        TRIGGERED,
        COOLDOWN
    }

    public record CheckResult(boolean blocked, Reason reason, long durationSeconds, long cooldownSeconds,
            long remainingSeconds) {

        public static CheckResult allow() {
            return new CheckResult(false, Reason.NONE, 0L, 0L, 0L);
        }

        public static CheckResult triggered(long durationSeconds, long cooldownSeconds) {
            return new CheckResult(true, Reason.TRIGGERED, durationSeconds, cooldownSeconds, durationSeconds);
        }

        public static CheckResult cooldown(long remainingSeconds) {
            return new CheckResult(true, Reason.COOLDOWN, 0L, 0L, remainingSeconds);
        }
    }

    private static final class PlayerState {
        private String lastNormalizedMessage;
        private long lastMessageAt;
        private long lastAllowedAt;
        private long slowmodeUntil;
        private int repeatCount;
    }

    public boolean clear(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        PlayerState state = states.get(uuid);
        if (state == null) {
            return false;
        }
        synchronized (state) {
            long now = System.currentTimeMillis();
            boolean active = state.slowmodeUntil > now;
            state.slowmodeUntil = 0L;
            state.repeatCount = 0;
            state.lastAllowedAt = 0L;
            return active;
        }
    }
}
