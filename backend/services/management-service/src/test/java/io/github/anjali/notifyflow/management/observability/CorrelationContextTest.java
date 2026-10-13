package io.github.anjali.notifyflow.management.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CorrelationContextTest {

    @Test
    void generatesValidCorrelationIdsAndRejectsUnsafeValues() {
        String generated = CorrelationContext.currentOrCreate();

        assertThat(generated).matches("[A-Za-z0-9._-]{8,128}");
        assertThat(CorrelationContext.normalize("abc-123")).isEqualTo("abc-123");
        assertThat(CorrelationContext.normalize("bad value\nnope")).isNull();
        assertThat(CorrelationContext.normalize("x".repeat(200))).isNull();
    }
}
