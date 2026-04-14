package io.quarkiverse.signals.test;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.signals.Receiver.ExecutionModel;
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
            .withApplicationRoot(root -> root.addClasses(Order.class, Priority.class));

    @Inject
    Signal<Order> order;

    @Inject
    @Any
    Signal<Order> anyOrder;

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

    @Test
    public void testSetQualifiers() {
        List<String> received = new CopyOnWriteArrayList<>();

        // Register a receiver with @Priority qualifier
        var reg = receivers.newReceiver(Order.class)
                .setQualifiers(Priority.Literal.INSTANCE)
                .notify(ctx -> {
                    received.add("priority_" + ctx.signal().id());
                });

        // Unqualified publish — should NOT reach the @Priority receiver
        anyOrder.publish(new Order("1"));
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertTrue(received.isEmpty(), "Qualified receiver should not receive unqualified signal");

        // Qualified publish — should reach the @Priority receiver
        anyOrder.select(Priority.Literal.INSTANCE).publish(new Order("2"));
        Awaitility.await().until(() -> received.size() >= 1);
        assertEquals(1, received.size());
        assertEquals("priority_2", received.get(0));

        reg.unregister();
    }

    @Test
    public void testSetQualifiersInvalid() {
        // @SuppressWarnings is not a qualifier — should throw
        assertThrows(IllegalArgumentException.class,
                () -> receivers.newReceiver(Order.class)
                        .setQualifiers(new SuppressWarnings() {
                            @Override
                            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                                return SuppressWarnings.class;
                            }

                            @Override
                            public String[] value() {
                                return new String[0];
                            }
                        }));
    }

    @Test
    public void testSetExecutionModel() {
        List<Boolean> virtualFlags = new CopyOnWriteArrayList<>();

        // Register with VIRTUAL_THREAD execution model
        var reg = receivers.newReceiver(Order.class)
                .setExecutionModel(ExecutionModel.VIRTUAL_THREAD)
                .notify(ctx -> {
                    virtualFlags.add(Thread.currentThread().isVirtual());
                });

        order.publish(new Order("vt"));
        Awaitility.await().until(() -> virtualFlags.size() >= 1);
        assertEquals(1, virtualFlags.size());
        assertTrue(virtualFlags.get(0), "Receiver should be executed on a virtual thread");

        reg.unregister();
    }

    record Order(String id) {
    }

    @Qualifier
    @Target({ FIELD, METHOD, PARAMETER })
    @Retention(RUNTIME)
    public @interface Priority {

        final class Literal extends AnnotationLiteral<Priority> implements Priority {
            public static final Literal INSTANCE = new Literal();
            private static final long serialVersionUID = 1L;
        }
    }
}
