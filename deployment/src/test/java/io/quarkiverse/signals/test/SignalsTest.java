package io.quarkiverse.signals.test;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.signals.Receives;
import io.quarkiverse.signals.Signal;
import io.quarkus.runtime.BlockingOperationControl;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;

public class SignalsTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(MyReceivers.class, Foo.class, Reactive.class));

    @Inject
    Signal<Foo> foo;

    @Inject
    @Reactive
    Signal<Foo> reactiveFoo;

    @Inject
    MyReceivers myReceivers;

    @Test
    public void testSignals() throws InterruptedException {
        myReceivers.sequence.clear();
        foo.publishAndForget(new Foo("pub_sub"));
        Awaitility.await().until(() -> myReceivers.sequence.size() >= 2);
        assertEquals(2, myReceivers.sequence.size());
        assertThat(myReceivers.sequence).contains("blocking_pub_sub", "blockingString_pub_sub");

        myReceivers.sequence.clear();
        foo.sendAndForget(new Foo("one_to_one"));
        Awaitility.await().until(() -> myReceivers.sequence.size() >= 1);
        assertEquals(1, myReceivers.sequence.size());
        assertThat(myReceivers.sequence).containsAnyOf("blocking_one_to_one", "blockingString_one_to_one");

        myReceivers.sequence.clear();
        Uni<String> uni = reactiveFoo.request(new Foo("req"), String.class);
        assertEquals("REQ", uni.ifNoItem()
                .after(Duration.ofSeconds(1))
                .fail()
                .await().indefinitely());
        assertEquals(1, myReceivers.sequence.size());
        assertThat(myReceivers.sequence).contains("reactiveString_req");

        myReceivers.sequence.clear();
        assertEquals("req", foo.request(new Foo("REQ"), String.class)
                .ifNoItem()
                .after(Duration.ofSeconds(1))
                .fail()
                .await().indefinitely());
        assertEquals(1, myReceivers.sequence.size());
        assertThat(myReceivers.sequence).contains("blockingString_REQ");

        // No receiver matches the response type
        myReceivers.sequence.clear();
        assertNull(foo.request(new Foo("REQ"), int.class)
                .ifNoItem()
                .after(Duration.ofSeconds(1))
                .fail()
                .await().indefinitely());
        assertEquals(0, myReceivers.sequence.size());
    }

    @Singleton
    public static class MyReceivers {

        final List<String> sequence = new CopyOnWriteArrayList<>();

        void blocking(MyService service, @Receives Foo foo) {
            if (!BlockingOperationControl.isBlockingAllowed()) {
                throw new IllegalStateException();
            }
            assertEquals(service.ping(), service.ping());
            sequence.add("blocking_" + foo.name());
        }

        String blockingString(@Receives Foo foo, BeanManager beanManager) {
            if (!BlockingOperationControl.isBlockingAllowed()) {
                throw new IllegalStateException();
            }
            if (beanManager == null) {
                throw new IllegalStateException("BeanManager is null");
            }
            sequence.add("blockingString_" + foo.name());
            return foo.name().toLowerCase();
        }

        Uni<String> reactiveString(@Receives @Reactive Foo foo) {
            if (BlockingOperationControl.isBlockingAllowed()) {
                return Uni.createFrom().failure(new IllegalStateException());
            }
            sequence.add("reactiveString_" + foo.name());
            return Uni.createFrom().item(foo.name().toUpperCase());
        }

    }

    record Foo(String name) {
    }

    @RequestScoped
    public static class MyService {

        private int val;

        public int ping() {
            return val;
        }

        @PostConstruct
        void init() {
            this.val = ThreadLocalRandom.current().nextInt();
        }

    }

    @Qualifier
    @Target({ FIELD, METHOD, PARAMETER })
    @Retention(RUNTIME)
    public @interface Reactive {

        final class Literal extends AnnotationLiteral<Reactive> implements Reactive {

            public static final Literal INSTANCE = new Literal();

            private static final long serialVersionUID = 1L;

        }

    }
}
