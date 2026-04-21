package io.quarkiverse.signals.it.cmd;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.quarkiverse.signals.Receives;
import io.quarkiverse.signals.Signal;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class SignalsApp implements QuarkusApplication {

    @Inject
    Signal<Cmd> signal;

    @Inject
    MyReceivers myReceivers;

    @Override
    public int run(String... args) {
        // Test request with blocking receiver
        String result = signal.request(new Cmd("hello"), String.class);
        if (!"HELLO".equals(result)) {
            System.err.println("request failed: expected HELLO, got " + result);
            return 1;
        }

        // Test send (fire-and-forget, unicast — round-robin picks one receiver)
        myReceivers.blockingCount.set(0);
        signal.send(new Cmd("fire"));
        // Give async delivery some time
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (myReceivers.blockingCount.get() == 0) {
            System.err.println("send failed: no receiver was invoked");
            return 1;
        }

        // Test publish (multicast, blocking await)
        myReceivers.blockingCount.set(0);
        signal.publishUni(new Cmd("multi"))
                .await().indefinitely();
        if (myReceivers.blockingCount.get() == 0) {
            System.err.println("publish failed: not all receivers were invoked (blocking="
                    + myReceivers.blockingCount.get() + ")");
            return 1;
        }

        System.out.println("All signal tests passed");
        return 0;
    }

    public record Cmd(String value) {
    }

    @Singleton
    public static class MyReceivers {

        final AtomicInteger blockingCount = new AtomicInteger();

        // Blocking signature → BLOCKING
        String toUpperCase(@Receives Cmd cmd) {
            blockingCount.incrementAndGet();
            return cmd.value().toUpperCase();
        }

    }
}
