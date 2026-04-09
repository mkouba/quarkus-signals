package io.quarkiverse.signals.test;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.signals.Signal;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies that emitting a signal with no matching receivers does not fail.
 */
public class SignalNoReceiverTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(Orphan.class));

    @Inject
    Signal<Orphan> orphan;

    @Test
    public void testPublishNoReceiver() {
        // Should not throw
        orphan.publish(new Orphan("nobody"));
    }

    @Test
    public void testSendNoReceiver() {
        // Should not throw
        orphan.send(new Orphan("nobody"));
    }

    @Test
    public void testRequestNoReceiver() {
        // Should return null when no receiver matches
        String result = orphan.request(new Orphan("nobody"), String.class)
                .ifNoItem()
                .after(Duration.ofSeconds(1))
                .fail()
                .await().indefinitely();
        assertNull(result);
    }

    record Orphan(String value) {
    }
}
