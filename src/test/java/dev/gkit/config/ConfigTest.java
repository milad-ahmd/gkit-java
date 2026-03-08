package dev.gkit.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {

    // -----------------------------------------------------------------------
    // Test POJOs for reflection-based loading
    // -----------------------------------------------------------------------

    static class SimpleConfig {
        @Config.Env("GKIT_TEST_STRING")
        @Config.DefaultValue("default-value")
        public String stringField;

        @Config.Env("GKIT_TEST_INT")
        @Config.DefaultValue("42")
        public int intField;

        @Config.Env("GKIT_TEST_LONG")
        @Config.DefaultValue("123456789")
        public long longField;

        @Config.Env("GKIT_TEST_DOUBLE")
        @Config.DefaultValue("3.14")
        public double doubleField;

        @Config.Env("GKIT_TEST_BOOL")
        @Config.DefaultValue("true")
        public boolean boolField;

        @Config.Env("GKIT_TEST_DURATION")
        @Config.DefaultValue("PT5S")
        public Duration durationField;
    }

    static class RequiredConfig {
        @Config.Env("GKIT_TEST_DEFINITELY_NOT_SET_REQUIRED")
        @Config.Required
        public String required;
    }

    static class OptionalConfig {
        @Config.Env("GKIT_TEST_DEFINITELY_NOT_SET_OPTIONAL")
        public String optional;
    }

    static class UnsupportedTypeConfig {
        @Config.Env("GKIT_TEST_UNSUPPORTED")
        @Config.DefaultValue("somevalue")
        public Object unsupportedField;
    }

    static class ListConfig {
        @Config.Env("GKIT_TEST_LIST")
        @Config.DefaultValue("a,b,c")
        public List<String> listField;
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("load fills String field from DefaultValue when env var absent")
    void loadFillsStringFromDefault() {
        SimpleConfig cfg = Config.load(new SimpleConfig());
        assertEquals("default-value", cfg.stringField);
    }

    @Test
    @DisplayName("load fills int field from DefaultValue")
    void loadFillsIntFromDefault() {
        SimpleConfig cfg = Config.load(new SimpleConfig());
        assertEquals(42, cfg.intField);
    }

    @Test
    @DisplayName("load fills long field from DefaultValue")
    void loadFillsLongFromDefault() {
        SimpleConfig cfg = Config.load(new SimpleConfig());
        assertEquals(123456789L, cfg.longField);
    }

    @Test
    @DisplayName("load fills double field from DefaultValue")
    void loadFillsDoubleFromDefault() {
        SimpleConfig cfg = Config.load(new SimpleConfig());
        assertEquals(3.14, cfg.doubleField, 0.001);
    }

    @Test
    @DisplayName("load fills boolean field from DefaultValue")
    void loadFillsBoolFromDefault() {
        SimpleConfig cfg = Config.load(new SimpleConfig());
        assertTrue(cfg.boolField);
    }

    @Test
    @DisplayName("load fills Duration field from DefaultValue")
    void loadFillsDurationFromDefault() {
        SimpleConfig cfg = Config.load(new SimpleConfig());
        assertEquals(Duration.ofSeconds(5), cfg.durationField);
    }

    @Test
    @DisplayName("load fills List field from comma-separated DefaultValue")
    void loadFillsListFromDefault() {
        ListConfig cfg = Config.load(new ListConfig());
        assertEquals(List.of("a", "b", "c"), cfg.listField);
    }

    @Test
    @DisplayName("load throws ConfigException for @Required field with no env var and no default")
    void loadThrowsForMissingRequiredField() {
        assertThrows(Config.ConfigException.class, () -> Config.load(new RequiredConfig()));
    }

    @Test
    @DisplayName("load leaves optional field null when env var absent and no default")
    void loadLeavesOptionalFieldNull() {
        OptionalConfig cfg = Config.load(new OptionalConfig());
        assertNull(cfg.optional);
    }

    @Test
    @DisplayName("load throws ConfigException for unsupported field type")
    void loadThrowsForUnsupportedType() {
        assertThrows(Config.ConfigException.class, () -> Config.load(new UnsupportedTypeConfig()));
    }

    @Test
    @DisplayName("ConfigException carries a descriptive message")
    void configExceptionHasMessage() {
        Config.ConfigException ex = new Config.ConfigException("test error");
        assertEquals("test error", ex.getMessage());
    }

    @Test
    @DisplayName("load returns the same object passed as dst")
    void loadReturnsSameObject() {
        SimpleConfig input = new SimpleConfig();
        SimpleConfig output = Config.load(input);
        assertSame(input, output);
    }

    @Test
    @DisplayName("load ignores fields not annotated with @Env")
    void loadIgnoresFieldsWithoutEnvAnnotation() {
        // This should succeed with no errors even though class has no @Env fields
        class NoEnvConfig {
            public String plain = "unchanged";
        }
        NoEnvConfig cfg = Config.load(new NoEnvConfig());
        assertEquals("unchanged", cfg.plain);
    }
}
