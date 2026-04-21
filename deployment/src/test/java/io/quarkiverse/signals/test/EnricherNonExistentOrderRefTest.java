package io.quarkiverse.signals.test;

import static org.junit.jupiter.api.Assertions.fail;

import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.signals.spi.ComponentOrder;
import io.quarkiverse.signals.spi.SignalMetadataEnricher;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.common.annotation.Identifier;

public class EnricherNonExistentOrderRefTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(MyEnricher.class))
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void testFailure() {
        fail();
    }

    @Identifier("my-enricher")
    @ComponentOrder(before = "nonexistent")
    @Singleton
    public static class MyEnricher implements SignalMetadataEnricher {

        @Override
        public void enrich(EnrichmentContext context) {
        }
    }
}
