package dev.gkit.validation;

import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Struct validation using a fluent builder API.
 *
 * <pre>{@code
 * Validation.Result result = Validation.validate()
 *     .field("email", email, Validation.required(), Validation.email())
 *     .field("quantity", qty, Validation.min(1), Validation.max(1000))
 *     .build();
 *
 * if (result.hasErrors()) {
 *     throw new ValidationException(result);
 * }
 * }</pre>
 */
public final class Validation {

    private Validation() {}

    @FunctionalInterface
    public interface Rule<T> {
        /** Returns an error message, or null if valid. */
        String check(T value);
    }

    public static final class Result {
        private final Map<String, List<String>> errors;

        Result(Map<String, List<String>> errors) {
            this.errors = Collections.unmodifiableMap(errors);
        }

        public boolean hasErrors() { return !errors.isEmpty(); }
        public Map<String, List<String>> getErrors() { return errors; }

        @Override public String toString() { return "ValidationResult" + errors; }
    }

    // -------------------------------------------------------------------------
    // Builder

    public static Builder validate() { return new Builder(); }

    public static final class Builder {
        private final Map<String, List<String>> errors = new LinkedHashMap<>();

        @SafeVarargs
        public final <T> Builder field(String name, T value, Rule<? super T>... rules) {
            List<String> fieldErrors = new ArrayList<>();
            for (Rule<? super T> rule : rules) {
                String msg = rule.check(value);
                if (msg != null) fieldErrors.add(msg);
            }
            if (!fieldErrors.isEmpty()) errors.put(name, fieldErrors);
            return this;
        }

        public Result build() { return new Result(new LinkedHashMap<>(errors)); }

        public void validate() {
            Result r = build();
            if (r.hasErrors()) throw new ValidationException(r);
        }
    }

    // -------------------------------------------------------------------------
    // Built-in rules

    public static Rule<Object> required() {
        return value -> {
            if (value == null) return "is required";
            if (value instanceof String s && s.isBlank()) return "is required";
            if (value instanceof Collection<?> c && c.isEmpty()) return "is required";
            return null;
        };
    }

    public static Rule<String> minLength(int n) {
        return value -> (value != null && value.length() >= n) ? null
            : "must be at least " + n + " characters";
    }

    public static Rule<String> maxLength(int n) {
        return value -> (value == null || value.length() <= n) ? null
            : "must be at most " + n + " characters";
    }

    public static Rule<Number> min(double n) {
        return value -> (value != null && value.doubleValue() >= n) ? null
            : "must be >= " + n;
    }

    public static Rule<Number> max(double n) {
        return value -> (value != null && value.doubleValue() <= n) ? null
            : "must be <= " + n;
    }

    public static Rule<String> email() {
        return value -> {
            if (value == null || value.isBlank()) return null;
            int at = value.lastIndexOf('@');
            if (at < 1) return "must be a valid email address";
            String domain = value.substring(at + 1);
            return domain.contains(".") ? null : "must be a valid email address";
        };
    }

    public static Rule<String> pattern(String regex) {
        Pattern p = Pattern.compile(regex);
        return value -> {
            if (value == null || value.isBlank()) return null;
            return p.matcher(value).matches() ? null : "must match pattern " + regex;
        };
    }

    public static Rule<String> oneOf(String... options) {
        Set<String> set = new HashSet<>(Arrays.asList(options));
        return value -> {
            if (value == null) return null;
            return set.contains(value) ? null : "must be one of " + Arrays.toString(options);
        };
    }

    public static Rule<String> url() {
        return value -> {
            if (value == null || value.isBlank()) return null;
            try {
                URI uri = URI.create(value);
                return (uri.isAbsolute() && uri.getHost() != null) ? null : "must be a valid URL";
            } catch (Exception e) {
                return "must be a valid URL";
            }
        };
    }

    // -------------------------------------------------------------------------
    // Exception

    public static class ValidationException extends RuntimeException {
        private final Result result;

        public ValidationException(Result result) {
            super("Validation failed: " + result.getErrors());
            this.result = result;
        }

        public Result getResult() { return result; }
    }
}
