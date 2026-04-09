package io.quarkiverse.signals.deployment;

import org.jboss.jandex.DotName;

import io.quarkiverse.signals.Receiver;
import io.quarkiverse.signals.Receives;
import io.quarkiverse.signals.Signal;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Uni;

class DotNames {

    static final DotName RECEIVES = DotName.createSimple(Receives.class);
    static final DotName SIGNAL = DotName.createSimple(Signal.class);
    static final DotName SIGNAL_CONTEXT = DotName.createSimple(Receiver.SignalContext.class);
    static final DotName UNI = DotName.createSimple(Uni.class);
    static final DotName RUN_ON_VIRTUAL_THREAD = DotName.createSimple(RunOnVirtualThread.class);
    static final DotName BLOCKING = DotName.createSimple(Blocking.class);
    static final DotName NON_BLOCKING = DotName.createSimple(NonBlocking.class);
}
