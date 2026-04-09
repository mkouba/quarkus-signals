package io.quarkiverse.signals.runtime;

import jakarta.enterprise.invoke.Invoker;

import io.quarkiverse.signals.Receiver;
import io.smallrye.mutiny.Uni;

public abstract class InvokerReceiver<SIGNAL, RESPONSE> implements Receiver<SIGNAL, RESPONSE> {

    private final Invoker<SIGNAL, RESPONSE> invoker;
    private final ReceiveInfo receiveInfo;

    public InvokerReceiver(Invoker<SIGNAL, RESPONSE> invoker, ReceiveInfo receiverInfo) {
        this.invoker = invoker;
        this.receiveInfo = receiverInfo;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Uni<RESPONSE> notify(SignalContext<SIGNAL> context) {
        Object[] args = new Object[receiveInfo.totalParams()];
        for (int i = 0; i < receiveInfo.totalParams(); i++) {
            if (i == receiveInfo.receiveArgPosition()) {
                args[i] = receiveInfo.receiveContext() ? context : context.signal();
            } else {
                args[i] = null;
            }
        }
        Object result;
        try {
            result = invoker.invoke(null, args);
        } catch (Exception e) {
            return Uni.createFrom().failure(e);
        }
        Uni<RESPONSE> ret;
        if (receiveInfo.returnsUni()) {
            ret = (Uni<RESPONSE>) result;
        } else {
            ret = (Uni<RESPONSE>) Uni.createFrom().item(result);
        }
        return ret;
    }

    public record ReceiveInfo(short receiveArgPosition, boolean receiveContext, short totalParams, boolean returnsUni) {
    }

}
