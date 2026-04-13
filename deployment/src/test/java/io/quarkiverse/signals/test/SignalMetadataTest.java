package io.quarkiverse.signals.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.signals.Receiver.SignalContext;
import io.quarkiverse.signals.Receives;
import io.quarkiverse.signals.Signal;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;

/**
 * Verifies that metadata attached via {@link Signal.Emission#withMeta(String, Object)}
 * is accessible in the receiver through {@link SignalContext#meta()}.
 */
public class SignalMetadataTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(Receivers.class, Event.class));

    @Inject
    Signal<Event> event;

    @Inject
    Receivers receivers;

    @Test
    public void testMetadata() {
        receivers.captured.clear();

        Uni<String> uni = event.unicast()
                .request(String.class)
                .withMeta("traceId", "abc-123")
                .withMeta("source", "test")
                .emit(new Event("hello"));

        String result = uni.ifNoItem()
                .after(Duration.ofSeconds(1))
                .fail()
                .await().indefinitely();

        assertEquals("hello:abc-123", result);
        assertEquals(1, receivers.captured.size());
        assertEquals("abc-123", receivers.captured.get(0));
    }

    @Singleton
    public static class Receivers {

        final List<String> captured = new CopyOnWriteArrayList<>();

        Uni<String> withMeta(@Receives SignalContext<Event> ctx) {
            String traceId = (String) ctx.meta().get("traceId");
            captured.add(traceId);
            return Uni.createFrom().item(ctx.signal().name() + ":" + traceId);
        }
    }

    record Event(String name) {
    }
}
