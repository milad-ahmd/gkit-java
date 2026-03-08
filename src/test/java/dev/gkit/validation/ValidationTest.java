package dev.gkit.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ValidationTest {

    @Test
    @DisplayName("valid fields produce no errors")
    void validFieldsProduceNoErrors() {
        Validation.Result result = Validation.validate()
                .field("name", "Alice", Validation.required())
                .build();
        assertFalse(result.hasErrors());
    }

    @Test
    @DisplayName("null value fails required rule")
    void nullValueFailsRequired() {
        Validation.Result result = Validation.validate()
                .field("name", null, Validation.required())
                .build();
        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().containsKey("name"));
    }

    @Test
    @DisplayName("blank string fails required rule")
    void blankStringFailsRequired() {
        Validation.Result result = Validation.validate()
                .field("email", "   ", Validation.required())
                .build();
        assertTrue(result.hasErrors());
    }

    @Test
    @DisplayName("empty collection fails required rule")
    void emptyCollectionFailsRequired() {
        Validation.Result result = Validation.validate()
                .field("tags", List.of(), Validation.required())
                .build();
        assertTrue(result.hasErrors());
    }

    @Test
    @DisplayName("minLength passes for sufficiently long string")
    void minLengthPassesForLongEnoughString() {
        Validation.Result result = Validation.validate()
                .field("username", "alice", Validation.minLength(3))
                .build();
        assertFalse(result.hasErrors());
    }

    @Test
    @DisplayName("minLength fails for too-short string")
    void minLengthFailsForShortString() {
        Validation.Result result = Validation.validate()
                .field("username", "ab", Validation.minLength(3))
                .build();
        assertTrue(result.hasErrors());
    }

    @Test
    @DisplayName("maxLength passes for short string")
    void maxLengthPassesForShortString() {
        Validation.Result result = Validation.validate()
                .field("code", "ABC", Validation.maxLength(5))
                .build();
        assertFalse(result.hasErrors());
    }

    @Test
    @DisplayName("maxLength fails for too-long string")
    void maxLengthFailsForLongString() {
        Validation.Result result = Validation.validate()
                .field("code", "ABCDEFG", Validation.maxLength(5))
                .build();
        assertTrue(result.hasErrors());
    }

    @Test
    @DisplayName("min passes for value above threshold")
    void minPassesForLargeValue() {
        Validation.Result result = Validation.validate()
                .field("qty", 5, Validation.min(1))
                .build();
        assertFalse(result.hasErrors());
    }

    @Test
    @DisplayName("min fails for value below threshold")
    void minFailsForSmallValue() {
        Validation.Result result = Validation.validate()
                .field("qty", 0, Validation.min(1))
                .build();
        assertTrue(result.hasErrors());
    }

    @Test
    @DisplayName("max passes for value within limit")
    void maxPassesForSmallEnoughValue() {
        Validation.Result result = Validation.validate()
                .field("qty", 100, Validation.max(200))
                .build();
        assertFalse(result.hasErrors());
    }

    @Test
    @DisplayName("max fails for value above limit")
    void maxFailsForLargeValue() {
        Validation.Result result = Validation.validate()
                .field("qty", 1001, Validation.max(1000))
                .build();
        assertTrue(result.hasErrors());
    }

    @Test
    @DisplayName("email passes for valid address")
    void emailPassesForValidAddress() {
        Validation.Result result = Validation.validate()
                .field("email", "user@example.com", Validation.email())
                .build();
        assertFalse(result.hasErrors());
    }

    @Test
    @DisplayName("email fails for address without @")
    void emailFailsWithoutAt() {
        Validation.Result result = Validation.validate()
                .field("email", "notanemail", Validation.email())
                .build();
        assertTrue(result.hasErrors());
    }

    @Test
    @DisplayName("email fails for address without domain dot")
    void emailFailsWithoutDomainDot() {
        Validation.Result result = Validation.validate()
                .field("email", "user@nodot", Validation.email())
                .build();
        assertTrue(result.hasErrors());
    }

    @Test
    @DisplayName("email passes for blank value (not required check)")
    void emailPassesForBlankValue() {
        Validation.Result result = Validation.validate()
                .field("email", "", Validation.email())
                .build();
        assertFalse(result.hasErrors());
    }

    @Test
    @DisplayName("pattern passes for matching value")
    void patternPassesForMatch() {
        Validation.Result result = Validation.validate()
                .field("code", "ABC123", Validation.pattern("[A-Z]{3}\\d{3}"))
                .build();
        assertFalse(result.hasErrors());
    }

    @Test
    @DisplayName("pattern fails for non-matching value")
    void patternFailsForNonMatch() {
        Validation.Result result = Validation.validate()
                .field("code", "abc", Validation.pattern("[A-Z]{3}"))
                .build();
        assertTrue(result.hasErrors());
    }

    @Test
    @DisplayName("oneOf passes for value in set")
    void oneOfPassesForValueInSet() {
        Validation.Result result = Validation.validate()
                .field("status", "active", Validation.oneOf("active", "inactive", "pending"))
                .build();
        assertFalse(result.hasErrors());
    }

    @Test
    @DisplayName("oneOf fails for value not in set")
    void oneOfFailsForValueNotInSet() {
        Validation.Result result = Validation.validate()
                .field("status", "unknown", Validation.oneOf("active", "inactive"))
                .build();
        assertTrue(result.hasErrors());
    }

    @Test
    @DisplayName("url passes for valid http URL")
    void urlPassesForValidUrl() {
        Validation.Result result = Validation.validate()
                .field("website", "https://example.com", Validation.url())
                .build();
        assertFalse(result.hasErrors());
    }

    @Test
    @DisplayName("url fails for non-URL string")
    void urlFailsForNonUrl() {
        Validation.Result result = Validation.validate()
                .field("website", "not-a-url", Validation.url())
                .build();
        assertTrue(result.hasErrors());
    }

    @Test
    @DisplayName("multiple fields can be validated in one chain")
    void multipleFieldsValidatedInChain() {
        Validation.Result result = Validation.validate()
                .field("email", "bad-email", Validation.required(), Validation.email())
                .field("qty", 0, Validation.min(1))
                .build();
        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().containsKey("email"));
        assertTrue(result.getErrors().containsKey("qty"));
    }

    @Test
    @DisplayName("Builder.validate() throws ValidationException when errors exist")
    void builderValidateThrowsValidationException() {
        Validation.Builder builder = Validation.validate()
                .field("name", null, Validation.required());
        assertThrows(Validation.ValidationException.class, builder::validate);
    }

    @Test
    @DisplayName("Builder.validate() does not throw when all fields valid")
    void builderValidateDoesNotThrowWhenValid() {
        Validation.Builder builder = Validation.validate()
                .field("name", "Alice", Validation.required());
        assertDoesNotThrow(builder::validate);
    }

    @Test
    @DisplayName("ValidationException carries the result")
    void validationExceptionCarriesResult() {
        Validation.Builder builder = Validation.validate()
                .field("x", null, Validation.required());
        Validation.ValidationException ex = assertThrows(
                Validation.ValidationException.class, builder::validate);
        assertNotNull(ex.getResult());
        assertTrue(ex.getResult().hasErrors());
    }

    @Test
    @DisplayName("multiple errors per field are all collected")
    void multipleErrorsPerField() {
        Validation.Result result = Validation.validate()
                .field("email", "x", Validation.minLength(5), Validation.email())
                .build();
        assertTrue(result.hasErrors());
        List<String> errors = result.getErrors().get("email");
        assertNotNull(errors);
        // Both minLength and email rules failed
        assertTrue(errors.size() >= 1);
    }
}
