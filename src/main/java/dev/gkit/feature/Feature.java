package dev.gkit.feature;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feature flag store with global on/off, percentage rollout, and allow-list modes.
 *
 * <pre>{@code
 * Feature.InMemoryStore store = Feature.InMemoryStore.fromEnv("FEATURE_");
 * store.set("dark-mode", new Feature.Flag.Builder().enabled(true).percentage(20).build());
 * if (store.isEnabledFor("dark-mode", userId)) { renderDarkTheme(); }
 * }</pre>
 */
public final class Feature {
    private Feature() {}

    public static final class Flag {
        private final boolean enabled;
        private final int percentage;
        private final List<String> allowList;

        private Flag(Builder b) {
            this.enabled = b.enabled;
            this.percentage = b.percentage;
            this.allowList = Collections.unmodifiableList(new ArrayList<>(b.allowList));
        }

        public boolean isEnabled() { return enabled; }
        public int getPercentage() { return percentage; }
        public List<String> getAllowList() { return allowList; }

        public static final class Builder {
            private boolean enabled;
            private int percentage;
            private List<String> allowList = new ArrayList<>();

            public Builder enabled(boolean v) { this.enabled = v; return this; }
            public Builder percentage(int v) { this.percentage = v; return this; }
            public Builder allowList(List<String> v) { this.allowList = v; return this; }
            public Flag build() { return new Flag(this); }
        }
    }

    public static final class InMemoryStore {
        private final ConcurrentHashMap<String, Flag> flags = new ConcurrentHashMap<>();

        public void set(String name, Flag flag) { flags.put(name, flag); }
        public Flag get(String name) { return flags.get(name); }
        public void delete(String name) { flags.remove(name); }

        public boolean isEnabled(String name) {
            Flag f = flags.get(name);
            if (f == null || !f.isEnabled()) return false;
            return (f.getPercentage() == 0 || f.getPercentage() >= 100) && f.getAllowList().isEmpty();
        }

        public boolean isEnabledFor(String name, String entityId) {
            Flag f = flags.get(name);
            if (f == null || !f.isEnabled()) return false;
            if (f.getAllowList().contains(entityId)) return true;
            if (f.getPercentage() > 0) return hashBucket(name + ":" + entityId, 100) < f.getPercentage();
            return true;
        }

        public Map<String, Flag> listAll() { return Collections.unmodifiableMap(flags); }

        public static InMemoryStore fromEnv(String prefix) {
            InMemoryStore store = new InMemoryStore();
            System.getenv().forEach((k, v) -> {
                if (k.startsWith(prefix)) {
                    String name = k.substring(prefix.length()).toLowerCase().replace('_', '-');
                    store.set(name, new Flag.Builder().enabled(Boolean.parseBoolean(v)).build());
                }
            });
            return store;
        }

        private static int hashBucket(String key, int buckets) {
            long h = 0xcbf29ce484222325L;
            for (byte b : key.getBytes(StandardCharsets.UTF_8)) {
                h ^= Byte.toUnsignedLong(b);
                h *= 0x100000001b3L;
            }
            return (int)(Long.toUnsignedLong(h) % buckets);
        }
    }
}
