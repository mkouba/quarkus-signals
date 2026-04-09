package io.quarkiverse.signals.test;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.signals.Receives;
import io.quarkiverse.signals.Signal;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;

public class SignalsTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClass(MyReceivers.class));

    @Inject
    Signal<Foo> foo;

    @Test
    public void testSignals() {
        MyReceivers.SEQUENCE.clear();
        foo.publish(new Foo("pub_sub"));
        assertEquals(2, MyReceivers.SEQUENCE.size());
        assertThat(MyReceivers.SEQUENCE).contains("blocking_pub_sub", "bblockingString_pub_sub");

        foo.send(new Foo("one_to_one"));
        // TODO assert sequence

        Uni<String> uni = foo.select(Reactive.Literal.INSTANCE).request(new Foo("req"), String.class);
        assertEquals("REQ", uni.await().indefinitely());
        // TODO assert sequence 
    }

    @Singleton
    public class MyReceivers {

        static List<String> SEQUENCE = new CopyOnWriteArrayList<>();

        void blocking(@Receives Foo foo) {
            SEQUENCE.add("blocking_" + foo.name());
        }

        String blockingString(@Receives Foo foo) {
            SEQUENCE.add("blockingString_" + foo.name());
            return foo.name().toLowerCase();
        }

        Uni<String> reactiveString(@Receives @Reactive Foo foo) {
            SEQUENCE.add("reactiveString_" + foo.name());
            return Uni.createFrom().item(foo.name().toUpperCase());
        }

    }

    record Foo(String name) {
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
