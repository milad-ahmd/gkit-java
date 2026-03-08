package dev.gkit.feature;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FeatureTest {

    // -----------------------------------------------------------------------
    // Flag
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Flag.Builder creates flag with correct values")
    void flagBuilderCreatesCorrectFlag() {
        Feature.Flag flag = new Feature.Flag.Builder()
                .enabled(true)
                .percentage(50)
                .allowList(List.of("user1", "user2"))
                .build();
        assertTrue(flag.isEnabled());
        assertEquals(50, flag.getPercentage());
        assertEquals(List.of("user1", "user2"), flag.getAllowList());
    }

    @Test
    @DisplayName("Flag defaults to disabled and 0 percentage")
    void flagDefaultsToDisabledZeroPercentage() {
        Feature.Flag flag = new Feature.Flag.Builder().build();
        assertFalse(flag.isEnabled());
        assertEquals(0, flag.getPercentage());
        assertTrue(flag.getAllowList().isEmpty());
    }

    // -----------------------------------------------------------------------
    // InMemoryStore — basic operations
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("set and get returns stored flag")
    void setAndGetReturnsFlag() {
        Feature.InMemoryStore store = new Feature.InMemoryStore();
        Feature.Flag flag = new Feature.Flag.Builder().enabled(true).build();
        store.set("my-feature", flag);
        assertSame(flag, store.get("my-feature"));
    }

    @Test
    @DisplayName("get returns null for unknown feature")
    void getReturnsNullForUnknownFeature() {
        Feature.InMemoryStore store = new Feature.InMemoryStore();
        assertNull(store.get("unknown"));
    }

    @Test
    @DisplayName("delete removes the feature")
    void deleteRemovesFeature() {
        Feature.InMemoryStore store = new Feature.InMemoryStore();
        store.set("f", new Feature.Flag.Builder().enabled(true).build());
        store.delete("f");
        assertNull(store.get("f"));
    }

    @Test
    @DisplayName("listAll returns all flags as unmodifiable map")
    void listAllReturnsAllFlags() {
        Feature.InMemoryStore store = new Feature.InMemoryStore();
        store.set("a", new Feature.Flag.Builder().enabled(true).build());
        store.set("b", new Feature.Flag.Builder().enabled(false).build());
        assertEquals(2, store.listAll().size());
        assertTrue(store.listAll().containsKey("a"));
        assertTrue(store.listAll().containsKey("b"));
    }

    // -----------------------------------------------------------------------
    // isEnabled
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("isEnabled returns true for globally enabled flag with no restrictions")
    void isEnabledTrueForGlobalFlag() {
        Feature.InMemoryStore store = new Feature.InMemoryStore();
        store.set("feature", new Feature.Flag.Builder().enabled(true).percentage(0).build());
        assertTrue(store.isEnabled("feature"));
    }

    @Test
    @DisplayName("isEnabled returns false for disabled flag")
    void isEnabledFalseForDisabledFlag() {
        Feature.InMemoryStore store = new Feature.InMemoryStore();
        store.set("feature", new Feature.Flag.Builder().enabled(false).build());
        assertFalse(store.isEnabled("feature"));
    }

    @Test
    @DisplayName("isEnabled returns false for unknown flag")
    void isEnabledFalseForUnknown() {
        Feature.InMemoryStore store = new Feature.InMemoryStore();
        assertFalse(store.isEnabled("unknown"));
    }

    @Test
    @DisplayName("isEnabled returns false when flag has allow list (entity-specific)")
    void isEnabledFalseWhenFlagHasAllowList() {
        Feature.InMemoryStore store = new Feature.InMemoryStore();
        store.set("f", new Feature.Flag.Builder()
                .enabled(true)
                .allowList(List.of("user1"))
                .build());
        // isEnabled (no entity) returns false when allow list is non-empty
        assertFalse(store.isEnabled("f"));
    }

    // -----------------------------------------------------------------------
    // isEnabledFor
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("isEnabledFor returns true for entity in allow list")
    void isEnabledForEntityInAllowList() {
        Feature.InMemoryStore store = new Feature.InMemoryStore();
        store.set("f", new Feature.Flag.Builder()
                .enabled(true)
                .allowList(List.of("user-vip"))
                .build());
        assertTrue(store.isEnabledFor("f", "user-vip"));
    }

    @Test
    @DisplayName("isEnabledFor returns false for entity not in allow list when percentage is 0")
    void isEnabledForEntityNotInAllowListZeroPercentage() {
        Feature.InMemoryStore store = new Feature.InMemoryStore();
        store.set("f", new Feature.Flag.Builder()
                .enabled(true)
                .allowList(List.of("user-vip"))
                .percentage(0)
                .build());
        assertFalse(store.isEnabledFor("f", "random-user"));
    }

    @Test
    @DisplayName("isEnabledFor with 100% rolls out to all entities")
    void isEnabledForHundredPercentRollout() {
        Feature.InMemoryStore store = new Feature.InMemoryStore();
        store.set("f", new Feature.Flag.Builder()
                .enabled(true)
                .percentage(100)
                .build());
        assertTrue(store.isEnabledFor("f", "anyone"));
        assertTrue(store.isEnabledFor("f", "another-user"));
    }

    @Test
    @DisplayName("isEnabledFor returns false for disabled flag regardless of entity")
    void isEnabledForReturnsFalseForDisabledFlag() {
        Feature.InMemoryStore store = new Feature.InMemoryStore();
        store.set("f", new Feature.Flag.Builder()
                .enabled(false)
                .allowList(List.of("user-vip"))
                .build());
        assertFalse(store.isEnabledFor("f", "user-vip"));
    }

    @Test
    @DisplayName("isEnabledFor returns false for unknown flag")
    void isEnabledForReturnsFalseForUnknown() {
        Feature.InMemoryStore store = new Feature.InMemoryStore();
        assertFalse(store.isEnabledFor("unknown", "user1"));
    }

    @Test
    @DisplayName("percentage rollout is consistent for same entity")
    void percentageRolloutIsConsistentForSameEntity() {
        Feature.InMemoryStore store = new Feature.InMemoryStore();
        store.set("f", new Feature.Flag.Builder().enabled(true).percentage(50).build());
        // Same entity should always get the same result (hash-based)
        boolean first = store.isEnabledFor("f", "stable-user-id");
        for (int i = 0; i < 10; i++) {
            assertEquals(first, store.isEnabledFor("f", "stable-user-id"));
        }
    }

    @Test
    @DisplayName("fromEnv loads flags from environment-like prefix")
    void fromEnvCreatesStore() {
        // fromEnv reads System.getenv(); we can only verify it doesn't throw
        Feature.InMemoryStore store = Feature.InMemoryStore.fromEnv("GKIT_TEST_NONEXISTENT_PREFIX_");
        assertNotNull(store);
    }
}
