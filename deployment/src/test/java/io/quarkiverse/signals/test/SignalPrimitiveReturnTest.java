package io.quarkiverse.signals.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.signals.Receives;
import io.quarkiverse.signals.Signal;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies that a receiver method accepting and returning a primitive type is matched correctly.
 */
public class SignalPrimitiveReturnTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(Receivers.class));

    @Inject
    Signal<Integer> signal;

    @Inject
    Receivers receivers;

    @Test
    public void testPrimitiveSignalAndResponse() {
        int result = signal.requestUni(42, Integer.class)
                .ifNoItem()
                .after(Duration.ofSeconds(1))
                .fail()
                .await().indefinitely();
        assertEquals(84, result);
    }

    @Singleton
    public static class Receivers {

        int doubleIt(@Receives int value) {
            return value * 2;
        }
    }
}
