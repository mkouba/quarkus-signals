package io.quarkiverse.signals.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.inject.Inject;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.signals.Receivers;
import io.quarkiverse.signals.Signal;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;

/**
 * Verifies programmatic registration and unregistration of receivers via {@link Receivers}.
 */
public class ReceiversTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(Order.class));

    @Inject
    Signal<Order> order;

    @Inject
    Receivers receivers;

    @Test
    public void testRegisterAndUnregister() {
        List<String> received = new CopyOnWriteArrayList<>();

        // Register a custom receiver
        var reg = receivers.newReceiver(Order.class)
                .notify(ctx -> {
                    received.add("listener_" + ctx.signal().id());
                });

        // Emit — the registered receiver should be invoked
        order.publish(new Order("1"));
        Awaitility.await().until(() -> received.size() >= 1);
        assertEquals(1, received.size());
        assertEquals("listener_1", received.get(0));

        // Unregister
        reg.unregister();
        received.clear();

        // Emit again — the receiver should no longer be invoked
        order.publish(new Order("2"));
        // Give some time for potential (unwanted) delivery
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertTrue(received.isEmpty(), "Receiver should not be invoked after unregister");
    }

    @Test
    public void testRegisterWithResponse() {
        Receivers.Registration reg = receivers.newReceiver(Order.class)
                .setResponseType(String.class)
                .notify(ctx -> {
                    return Uni.createFrom().item("processed_" + ctx.signal().id());
                });

        String result = order.request(new Order("42"), String.class)
                .ifNoItem()
                .after(Duration.ofSeconds(1))
                .fail()
                .await().indefinitely();
        assertEquals("processed_42", result);

        reg.unregister();
    }

    record Order(String id) {
    }
}
