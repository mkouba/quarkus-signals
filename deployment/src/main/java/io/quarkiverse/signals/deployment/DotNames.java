package io.quarkiverse.signals.deployment;

import org.jboss.jandex.DotName;

import io.quarkiverse.signals.Receiver;
import io.quarkiverse.signals.Receives;
import io.quarkiverse.signals.Signal;
import io.quarkiverse.signals.spi.ComponentOrder;
import io.quarkiverse.signals.spi.ReceiverInterceptor;
import io.quarkiverse.signals.spi.SignalMetadataEnricher;
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
    static final DotName IDENTIFIER = DotName.createSimple("io.smallrye.common.annotation.Identifier");
    static final DotName COMPONENT_ORDER = DotName.createSimple(ComponentOrder.class);
    static final DotName SIGNAL_METADATA_ENRICHER = DotName.createSimple(SignalMetadataEnricher.class);
    static final DotName RECEIVER_INTERCEPTOR = DotName.createSimple(ReceiverInterceptor.class);
}
