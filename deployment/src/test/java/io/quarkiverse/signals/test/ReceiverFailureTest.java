package io.quarkiverse.signals.test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.signals.Receivers;
import io.quarkiverse.signals.Receives;
import io.quarkiverse.signals.Signal;
import io.quarkiverse.signals.SignalContext;
import io.quarkiverse.signals.spi.Receiver.ExecutionModel;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.CompositeException;
import io.smallrye.mutiny.Uni;

/**
 * Verifies that exceptions thrown by receivers are propagated through the {@link Uni}
 * returned by {@link Signal.Emission#emit(Object)} for all execution models.
 * Tests both programmatic and declarative receivers.
 */
public class ReceiverFailureTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(Cmd.class,
                    WorkerCmd.class, EventLoopCmd.class, VirtualCmd.class,
                    DeclarativeReceivers.class));

    @Inject
    Signal<Cmd> cmd;

    @Inject
    Signal<WorkerCmd> workerSignal;

    @Inject
    Signal<EventLoopCmd> eventLoopSignal;

    @Inject
    Signal<VirtualCmd> virtualSignal;

    @Inject
    Receivers receivers;

    @Test
    public void testPublishFailureWorkerThread() {
        var reg = receivers.newReceiver(Cmd.class)
                .setExecutionModel(ExecutionModel.BLOCKING)
                .notify(new Consumer<SignalContext<Cmd>>() {

                    @Override
                    public void accept(SignalContext<Cmd> t) {
                        throw new IllegalStateException("worker-boom");
                    }
                });
        try {
            var failure = assertThrows(CompositeException.class,
                    () -> cmd.publishUni(new Cmd("w"))
                            .ifNoItem().after(Duration.ofSeconds(5)).fail()
                            .await().indefinitely());
            assertInstanceOf(IllegalStateException.class, failure.getCauses().get(0));
        } finally {
            reg.unregister();
        }
    }

    @Test
    public void testPublishFailureVirtualThread() {
        var reg = receivers.newReceiver(Cmd.class)
                .setExecutionModel(ExecutionModel.VIRTUAL_THREAD)
                .notify(new Consumer<SignalContext<Cmd>>() {

                    @Override
                    public void accept(SignalContext<Cmd> t) {
                        throw new IllegalStateException("virtual-boom");
                    }
                });
        try {
            var failure = assertThrows(CompositeException.class,
                    () -> cmd.publishUni(new Cmd("v"))
                            .ifNoItem().after(Duration.ofSeconds(5)).fail()
                            .await().indefinitely());
            assertInstanceOf(IllegalStateException.class, failure.getCauses().get(0));
        } finally {
            reg.unregister();
        }
    }

    @Test
    public void testPublishFailureEventLoop() {
        var reg = receivers.newReceiver(Cmd.class)
                .setExecutionModel(ExecutionModel.NON_BLOCKING)
                .notify(new Consumer<SignalContext<Cmd>>() {

                    @Override
                    public void accept(SignalContext<Cmd> t) {
                        throw new IllegalStateException("eventloop-boom");
                    }
                });
        try {
            var failure = assertThrows(CompositeException.class,
                    () -> cmd.publishUni(new Cmd("e"))
                            .ifNoItem().after(Duration.ofSeconds(5)).fail()
                            .await().indefinitely());
            assertInstanceOf(IllegalStateException.class, failure.getCauses().get(0));
        } finally {
            reg.unregister();
        }
    }

    @Test
    public void testSendFailureWorkerThread() {
        var reg = receivers.newReceiver(Cmd.class)
                .setExecutionModel(ExecutionModel.BLOCKING)
                .notify(new Consumer<SignalContext<Cmd>>() {

                    @Override
                    public void accept(SignalContext<Cmd> t) {
                        throw new IllegalStateException("worker-boom");
                    }
                });
        try {
            assertThrows(IllegalStateException.class,
                    () -> cmd.sendUni(new Cmd("w"))
                            .ifNoItem().after(Duration.ofSeconds(5)).fail()
                            .await().indefinitely());
        } finally {
            reg.unregister();
        }
    }

    @Test
    public void testSendFailureVirtualThread() {
        var reg = receivers.newReceiver(Cmd.class)
                .setExecutionModel(ExecutionModel.VIRTUAL_THREAD)
                .notify(new Consumer<SignalContext<Cmd>>() {

                    @Override
                    public void accept(SignalContext<Cmd> t) {
                        throw new IllegalStateException("virtual-boom");
                    }
                });
        try {
            assertThrows(IllegalStateException.class,
                    () -> cmd.sendUni(new Cmd("v"))
                            .ifNoItem().after(Duration.ofSeconds(5)).fail()
                            .await().indefinitely());
        } finally {
            reg.unregister();
        }
    }

    @Test
    public void testSendFailureEventLoop() {
        var reg = receivers.newReceiver(Cmd.class)
                .setExecutionModel(ExecutionModel.NON_BLOCKING)
                .notify(new Consumer<SignalContext<Cmd>>() {

                    @Override
                    public void accept(SignalContext<Cmd> t) {
                        throw new IllegalStateException("eventloop-boom");
                    }
                });
        try {
            assertThrows(IllegalStateException.class,
                    () -> cmd.sendUni(new Cmd("e"))
                            .ifNoItem().after(Duration.ofSeconds(5)).fail()
                            .await().indefinitely());
        } finally {
            reg.unregister();
        }
    }

    @Test
    public void testRequestFailureWorkerThread() {
        var reg = receivers.newReceiver(Cmd.class)
                .setResponseType(String.class)
                .setExecutionModel(ExecutionModel.BLOCKING)
                .notify(new Function<SignalContext<Cmd>, Uni<String>>() {

                    @Override
                    public Uni<String> apply(SignalContext<Cmd> ctx) {
                        throw new IllegalStateException("worker-boom");
                    }
                });
        try {
            assertThrows(IllegalStateException.class,
                    () -> cmd.requestUni(new Cmd("w"), String.class)
                            .ifNoItem().after(Duration.ofSeconds(5)).fail()
                            .await().indefinitely());
        } finally {
            reg.unregister();
        }
    }

    @Test
    public void testRequestFailureVirtualThread() {
        var reg = receivers.newReceiver(Cmd.class)
                .setResponseType(String.class)
                .setExecutionModel(ExecutionModel.VIRTUAL_THREAD)
                .notify(new Function<SignalContext<Cmd>, Uni<String>>() {

                    @Override
                    public Uni<String> apply(SignalContext<Cmd> ctx) {
                        throw new IllegalStateException("virtual-boom");
                    }
                });
        try {
            assertThrows(IllegalStateException.class,
                    () -> cmd.requestUni(new Cmd("v"), String.class)
                            .ifNoItem().after(Duration.ofSeconds(5)).fail()
                            .await().indefinitely());
        } finally {
            reg.unregister();
        }
    }

    @Test
    public void testRequestFailureEventLoop() {
        var reg = receivers.newReceiver(Cmd.class)
                .setResponseType(String.class)
                .setExecutionModel(ExecutionModel.NON_BLOCKING)
                .notify(new Function<SignalContext<Cmd>, Uni<String>>() {

                    @Override
                    public Uni<String> apply(SignalContext<Cmd> ctx) {
                        throw new IllegalStateException("eventloop-boom");
                    }
                });
        try {
            assertThrows(IllegalStateException.class,
                    () -> cmd.requestUni(new Cmd("e"), String.class)
                            .ifNoItem().after(Duration.ofSeconds(5)).fail()
                            .await().indefinitely());
        } finally {
            reg.unregister();
        }
    }

    @Test
    public void testDeclarativePublishFailureWorkerThread() {
        var failure = assertThrows(CompositeException.class,
                () -> workerSignal.publishUni(new WorkerCmd())
                        .ifNoItem().after(Duration.ofSeconds(5)).fail()
                        .await().indefinitely());
        assertInstanceOf(IllegalStateException.class, failure.getCauses().get(0));
    }

    @Test
    public void testDeclarativePublishFailureEventLoop() {
        var failure = assertThrows(CompositeException.class,
                () -> eventLoopSignal.publishUni(new EventLoopCmd())
                        .ifNoItem().after(Duration.ofSeconds(5)).fail()
                        .await().indefinitely());
        assertInstanceOf(IllegalStateException.class, failure.getCauses().get(0));
    }

    @Test
    public void testDeclarativePublishFailureVirtualThread() {
        var failure = assertThrows(CompositeException.class,
                () -> virtualSignal.publishUni(new VirtualCmd())
                        .ifNoItem().after(Duration.ofSeconds(5)).fail()
                        .await().indefinitely());
        assertInstanceOf(IllegalStateException.class, failure.getCauses().get(0));
    }

    @Test
    public void testDeclarativeSendFailureWorkerThread() {
        assertThrows(IllegalStateException.class,
                () -> workerSignal.sendUni(new WorkerCmd())
                        .ifNoItem().after(Duration.ofSeconds(5)).fail()
                        .await().indefinitely());
    }

    @Test
    public void testDeclarativeSendFailureEventLoop() {
        assertThrows(IllegalStateException.class,
                () -> eventLoopSignal.sendUni(new EventLoopCmd())
                        .ifNoItem().after(Duration.ofSeconds(5)).fail()
                        .await().indefinitely());
    }

    @Test
    public void testDeclarativeSendFailureVirtualThread() {
        assertThrows(IllegalStateException.class,
                () -> virtualSignal.sendUni(new VirtualCmd())
                        .ifNoItem().after(Duration.ofSeconds(5)).fail()
                        .await().indefinitely());
    }

    record Cmd(String id) {
    }

    record WorkerCmd() {
    }

    record EventLoopCmd() {
    }

    record VirtualCmd() {
    }

    @Singleton
    public static class DeclarativeReceivers {

        void onWorker(@Receives WorkerCmd cmd) {
            throw new IllegalStateException("declarative-worker-boom");
        }

        Uni<Void> onEventLoop(@Receives EventLoopCmd cmd) {
            throw new IllegalStateException("declarative-eventloop-boom");
        }

        @RunOnVirtualThread
        void onVirtual(@Receives VirtualCmd cmd) {
            throw new IllegalStateException("declarative-virtual-boom");
        }
    }
}
