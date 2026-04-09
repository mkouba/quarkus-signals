package io.quarkiverse.signals.runtime;

import java.util.concurrent.Callable;

import io.quarkiverse.signals.Receiver;
import io.quarkiverse.signals.Receiver.ExecutionModel;
import io.quarkiverse.signals.Receiver.SignalContext;
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle;
import io.quarkus.virtual.threads.VirtualThreadsRecorder;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.vertx.UniHelper;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class VertxReceiverExecutor implements ReceiverExecutor {

    @Inject
    Vertx vertx;

    @Override
    public <SIGNAL, RESPONSE> Uni<RESPONSE> execute(Receiver<SIGNAL, RESPONSE> receiver, SignalContext<SIGNAL> signalContext) {
        Context context = VertxContext.createNewDuplicatedContext(vertx.getOrCreateContext());
        VertxContextSafetyToggle.setContextSafe(context, true);
        Promise<RESPONSE> ret = Promise.promise();
        context.runOnContext(v -> {
            execute(receiver.executionModel(), new Callable<Uni<RESPONSE>>() {
                @Override
                public Uni<RESPONSE> call() throws Exception {
                    return receiver.notify(signalContext);
                }
            }).onComplete(r -> {
                if (r.failed()) {
                    ret.fail(r.cause());
                } else {
                    ret.complete();
                }
            });
        });
        return UniHelper.toUni(ret.future());
    }

    protected <RESULT> Future<RESULT> execute(ExecutionModel executionModel, Callable<Uni<RESULT>> action) {
        Promise<RESULT> ret = Promise.promise();
        if (executionModel == ExecutionModel.VIRTUAL_THREAD) {
            VirtualThreadsRecorder.getCurrent().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        action.call().subscribe().with(ret::complete, ret::fail);
                    } catch (Throwable e) {
                        ret.fail(e);
                    }
                }
            });
        } else if (executionModel == ExecutionModel.WORKER_THREAD) {
            vertx.executeBlocking(new Callable<Void>() {
                @Override
                public Void call() {
                    try {
                        action.call().subscribe().with(ret::complete, ret::fail);
                    } catch (Throwable e) {
                        ret.fail(e);
                    }
                    return null;
                }
            }, false);
        } else {
            try {
                action.call().subscribe().with(ret::complete, ret::fail);
            } catch (Throwable e) {
                ret.fail(e);
            }
        }
        return ret.future();
    }

}
