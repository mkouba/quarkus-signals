package io.quarkiverse.signals.runtime;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import io.quarkiverse.signals.spi.Receiver.ExecutionModel;
import io.quarkus.virtual.threads.VirtualThreadsRecorder;
import io.smallrye.mutiny.Uni;

@Singleton
public class VirtualThreadReceiverExecutor extends DefaultBlockingReceiverExecutor {

    @Inject
    ExecutorService executorService;

    @Override
    public boolean supportsExecutionModel(ExecutionModel val) {
        return val == ExecutionModel.VIRTUAL_THREAD || val == ExecutionModel.BLOCKING;
    }

    protected <RESULT> CompletableFuture<RESULT> execute(ExecutionModel executionModel, Callable<Uni<RESULT>> action) {
        if (executionModel == ExecutionModel.VIRTUAL_THREAD) {
            CompletableFuture<RESULT> ret = new CompletableFuture<>();
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
            return ret;
        }
        return super.execute(executionModel, action);
    }

}
