package io.quarkiverse.signals.runtime;

import io.quarkiverse.signals.Receiver;
import io.quarkiverse.signals.Receiver.SignalContext;
import io.smallrye.mutiny.Uni;

public interface ReceiverExecutor {

    <SIGNAL, RESPONSE> Uni<RESPONSE> execute(Receiver<SIGNAL, RESPONSE> receiver, SignalContext<SIGNAL> context);

}
