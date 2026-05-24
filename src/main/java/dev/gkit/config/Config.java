package dev.gkit.config;
import java.lang.annotation.*;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.*;

/**
 * Loads application configuration from environment variables into annotated fields.
 * Annotate fields with @Env("VAR_NAME") and optionally @DefaultValue and @Required.
 *
 * <pre>{@code
 * public class AppConfig {
 *     @Env("DB_URL") @Required public String dbUrl;
 *     @Env("PORT") @DefaultValue("8080") public int port;
 * }
 * AppConfig cfg = Config.load(new AppConfig());
 * }</pre>
 */
public final class Config {
    private Config() {}

    /** Specifies the environment variable name for this field. */
    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
    public @interface Env { String value(); }

    /** Fallback value when env var is unset. */
    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
    public @interface DefaultValue { String value(); }

    /** Causes load() to throw if the env var is unset and no default is provided. */
    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
    public @interface Required {}

    /** Fills all @Env-annotated fields in dst from environment variables. */
    public static <T> T load(T dst) {
        List<String> errors = new ArrayList<>();
        for (Field f : dst.getClass().getDeclaredFields()) {
            Env env = f.getAnnotation(Env.class);
            if (env == null) continue;
            String raw = System.getenv(env.value());
            if (raw == null || raw.isEmpty()) {
                DefaultValue dv = f.getAnnotation(DefaultValue.class);
                if (dv != null) raw = dv.value();
            }
            if (raw == null || raw.isEmpty()) {
                if (f.isAnnotationPresent(Required.class))
                    errors.add("Required env var not set: " + env.value());
                continue;
            }
            f.setAccessible(true);
            try { setField(dst, f, raw); }
            catch (Exception e) { errors.add("Invalid value for " + env.value() + ": " + e.getMessage()); }
        }
        if (!errors.isEmpty()) throw new ConfigException(String.join("; ", errors));
        return dst;
    }

    private static void setField(Object dst, Field f, String raw) throws Exception {
        Class<?> t = f.getType();
        if (t == String.class) f.set(dst, raw);
        else if (t == int.class || t == Integer.class) f.setInt(dst, Integer.parseInt(raw));
        else if (t == long.class || t == Long.class) f.setLong(dst, Long.parseLong(raw));
        else if (t == double.class || t == Double.class) f.setDouble(dst, Double.parseDouble(raw));
        else if (t == boolean.class || t == Boolean.class) f.setBoolean(dst, Boolean.parseBoolean(raw));
        else if (t == Duration.class) f.set(dst, Duration.parse(raw));
        else if (t == List.class) f.set(dst, Arrays.asList(raw.split(",")));
        else throw new ConfigException("Unsupported field type: " + t.getName());
    }

    /** Thrown when configuration loading fails. */
    public static class ConfigException extends RuntimeException {
        public ConfigException(String msg) { super(msg); }
    }
}
