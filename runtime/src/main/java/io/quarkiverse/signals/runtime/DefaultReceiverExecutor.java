package io.quarkiverse.signals.runtime;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.signals.Receiver;
import io.quarkiverse.signals.Receiver.ExecutionModel;
import io.quarkiverse.signals.Receiver.SignalContext;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.virtual.threads.VirtualThreadsRecorder;
import io.smallrye.mutiny.Uni;

@Singleton
public class DefaultReceiverExecutor implements ReceiverExecutor {

    private static final Logger LOG = Logger.getLogger(DefaultReceiverExecutor.class);

    @Inject
    ExecutorService executorService;

    @Override
    public boolean supportsExecutionModel(ExecutionModel val) {
        return val == ExecutionModel.VIRTUAL_THREAD || val == ExecutionModel.WORKER_THREAD;
    }

    @Override
    public <SIGNAL, RESPONSE> Uni<RESPONSE> execute(Receiver<SIGNAL, RESPONSE> receiver, SignalContext<SIGNAL> context) {
        ExecutionModel executionModel = receiver.executionModel();
        if (executionModel == ExecutionModel.EVENT_LOOP) {
            throw new IllegalStateException("The ExecutionModel.EVENT_LOOP is not supported");
        }
        LOG.infof("Notify %s [signal=%s, emission=%s]", receiver, context.signalType(),
                context.emissionType());
        CompletableFuture<RESPONSE> ret = execute(executionModel, new Callable<Uni<RESPONSE>>() {
            @Override
            public Uni<RESPONSE> call() throws Exception {
                // Activate new request context
                ManagedContext requestContext = Arc.container().requestContext();
                requestContext.activate();
                return receiver.notify(context).eventually(requestContext::terminate);
            }
        });
        return Uni.createFrom().completionStage(ret);
    }

    protected <RESULT> CompletableFuture<RESULT> execute(ExecutionModel executionModel, Callable<Uni<RESULT>> action) {
        CompletableFuture<RESULT> ret = new CompletableFuture<>();
        if (executionModel == ExecutionModel.VIRTUAL_THREAD) {
            VirtualThreadsRecorder.getCurrent().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        action.call().subscribe().with(ret::complete, ret::completeExceptionally);
                    } catch (Throwable e) {
                        ret.completeExceptionally(e);
                    }
                }
            });
        } else if (executionModel == ExecutionModel.WORKER_THREAD) {
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        action.call().subscribe().with(ret::complete, ret::completeExceptionally);
                    } catch (Throwable e) {
                        ret.completeExceptionally(e);
                    }
                }
            });
        }
        return ret;
    }

}
