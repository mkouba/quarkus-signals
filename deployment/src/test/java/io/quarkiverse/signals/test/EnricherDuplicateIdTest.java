package io.quarkiverse.signals.test;

import static org.junit.jupiter.api.Assertions.fail;

import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.signals.spi.SignalMetadataEnricher;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.common.annotation.Identifier;

public class EnricherDuplicateIdTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(FooEnricher.class, BarEnricher.class))
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void testFailure() {
        fail();
    }

    @Identifier("foo")
    @Singleton
    public static class FooEnricher implements SignalMetadataEnricher {

        @Override
        public void enrich(EnrichmentContext context) {
        }
    }

    @Identifier("foo")
    @Singleton
    public static class BarEnricher implements SignalMetadataEnricher {

        @Override
        public void enrich(EnrichmentContext context) {
        }
    }
}
