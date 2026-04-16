package io.quarkiverse.signals.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.signals.Receivers;
import io.quarkiverse.signals.Signal;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;

/**
 * Verifies that each subscription to the {@link Uni} returned by the reactive emission methods
 * ({@link Signal#publish(Object)}, {@link Signal#send(Object)}, {@link Signal#request(Object, Class)})
 * triggers a new, independent signal emission.
 */
public class ResubscriptionTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(Ping.class));

    @Inject
    Signal<Ping> signal;

    @Inject
    Receivers receivers;

    @Test
    public void testPublishEmitsOnEachSubscription() {
        List<String> received = new CopyOnWriteArrayList<>();

        var reg = receivers.newReceiver(Ping.class)
                .notify(ctx -> {
                    received.add(ctx.signal().id());
                });
        try {
            Uni<Void> uni = signal.publish(new Ping("p"));

            // First subscription
            uni.ifNoItem().after(Duration.ofSeconds(5)).fail()
                    .await().indefinitely();
            assertEquals(1, received.size());

            // Second subscription of the same Uni — should trigger a new emission
            uni.ifNoItem().after(Duration.ofSeconds(5)).fail()
                    .await().indefinitely();
            assertEquals(2, received.size());
        } finally {
            reg.unregister();
        }
    }

    @Test
    public void testSendEmitsOnEachSubscription() {
        AtomicInteger count = new AtomicInteger();

        var reg = receivers.newReceiver(Ping.class)
                .notify(ctx -> {
                    count.incrementAndGet();
                });
        try {
            Uni<Void> uni = signal.send(new Ping("s"));

            // First subscription
            uni.ifNoItem().after(Duration.ofSeconds(5)).fail()
                    .await().indefinitely();
            assertEquals(1, count.get());

            // Second subscription of the same Uni — should trigger a new emission
            uni.ifNoItem().after(Duration.ofSeconds(5)).fail()
                    .await().indefinitely();
            assertEquals(2, count.get());
        } finally {
            reg.unregister();
        }
    }

    @Test
    public void testRequestEmitsOnEachSubscription() {
        AtomicInteger count = new AtomicInteger();

        var reg = receivers.newReceiver(Ping.class)
                .setResponseType(String.class)
                .notify(ctx -> {
                    return Uni.createFrom().item("reply_" + count.incrementAndGet());
                });
        try {
            Uni<String> uni = signal.request(new Ping("r"), String.class);

            // First subscription
            String first = uni.ifNoItem().after(Duration.ofSeconds(5)).fail()
                    .await().indefinitely();
            assertEquals("reply_1", first);

            // Second subscription of the same Uni — should trigger a new emission
            String second = uni.ifNoItem().after(Duration.ofSeconds(5)).fail()
                    .await().indefinitely();
            assertEquals("reply_2", second);
        } finally {
            reg.unregister();
        }
    }

    record Ping(String id) {
    }
}
