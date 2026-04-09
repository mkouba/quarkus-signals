package io.quarkiverse.signals.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Set;

import io.quarkiverse.signals.Receiver;

class ReceiverManager {

    ReceiverExecutor receiverExecutor() {
        // TODO
        return null;
    }
    
    <T> Collection<Receiver<T, ?>> resolveReceivers(Type signalType,
            Set<Annotation> qualifiers) {
        // TODO
        return null;
    }

}
