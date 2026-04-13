package io.quarkiverse.signals.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.BeanContainer;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.signals.Receiver;
import io.quarkiverse.signals.Receivers;
import io.quarkiverse.signals.runtime.ReceiverDefinitionImpl.CallbackReceiver;
import io.quarkiverse.signals.runtime.SignalsRecorder.SignalsContext;
import io.quarkus.arc.Arc;

@Singleton
public class ReceiverManager implements Receivers {

    private static final Logger LOG = Logger.getLogger(ReceiverManager.class);

    private final ConcurrentMap<String, Receiver<?, ?>> receivers;

    private final ConcurrentMap<SignalResolvable, RoundRobin<Receiver<?, ?>>> resolvedReceivers;

    private final ReceiverExecutor executor;

    private final BeanContainer beanContainer;

    ReceiverManager(SignalsContext signalsContext, ReceiverExecutor executor) {
        List<String> invokerReceiversClasses = signalsContext.receiversClasses();
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        this.receivers = new ConcurrentHashMap<>();
        for (String irc : invokerReceiversClasses) {
            try {
                Receiver<?, ?> r = (Receiver<?, ?>) tccl.loadClass(irc).getConstructor().newInstance();
                receivers.put(irc, r);
            } catch (Exception e) {
                LOG.errorf(e, "Unable to instantiate InvokerReceiver: %s", irc);
            }
        }
        this.resolvedReceivers = new ConcurrentHashMap<>();
        this.executor = executor;
        this.beanContainer = Arc.container().beanManager();
    }

    ReceiverExecutor receiverExecutor() {
        return executor;
    }

    List<Receiver<?, ?>> resolveReceivers(Type signalType,
            Set<Annotation> qualifiers) {
        RoundRobin<Receiver<?, ?>> resolved = resolvedReceivers
                .computeIfAbsent(new SignalResolvable(signalType, qualifiers, null), this::computeRoundRobin);
        return resolved.elements();
    }

    private RoundRobin<Receiver<?, ?>> computeRoundRobin(SignalResolvable signalResolvable) {
        List<Receiver<?, ?>> matching = new ArrayList<>();
        for (Receiver<?, ?> receiver : receivers.values()) {
            // Reuse the rules for CDI events
            var qualifiers = effectiveQualifiers(receiver.qualifiers());
            if (beanContainer.isMatchingEvent(signalResolvable.signalType(), signalResolvable.qualifiers(),
                    receiver.signalType(), qualifiers)) {
                if (signalResolvable.responseType() != null
                        && (receiver.responseType() == null || !beanContainer.isMatchingEvent(receiver.responseType(), Set.of(),
                                signalResolvable.responseType(),
                                Set.of()))) {
                    // response type of the receiver is not assignable to the response type of the signal
                    continue;
                }
                matching.add(receiver);
            }
        }
        LOG.infof("Computed %s receivers for: %s", matching.size(), signalResolvable);
        return new RoundRobin<Receiver<?, ?>>(matching.toArray(new Receiver<?, ?>[0]));
    }

    Receiver<?, ?> nextReceiver(Type signalType,
            Set<Annotation> qualifiers, Type responseType) {
        RoundRobin<Receiver<?, ?>> resolved = resolvedReceivers
                .computeIfAbsent(new SignalResolvable(signalType, qualifiers, responseType), this::computeRoundRobin);
        return resolved.next();
    }

    @Override
    public <SIGNAL> ReceiverDefinition<SIGNAL, Void> newReceiver(Class<SIGNAL> signalType) {
        return new ReceiverDefinitionImpl<>(signalType, beanContainer, this::register);
    }

    @Override
    public <SIGNAL> ReceiverDefinition<SIGNAL, Void> newReceiver(TypeLiteral<SIGNAL> signalType) {
        return new ReceiverDefinitionImpl<>(signalType.getType(), beanContainer, this::register);
    }

    private Registration register(CallbackReceiver<?, ?> receiver) {
        receivers.put(receiver.id(), receiver);
        invalidateCache(receiver);
        return () -> {
            receivers.remove(receiver.id());
            invalidateCache(receiver);
        };
    }

    private void invalidateCache(Receiver<?, ?> receiver) {
        var qualifiers = effectiveQualifiers(receiver.qualifiers());
        resolvedReceivers.keySet().removeIf(key -> {
            if (!beanContainer.isMatchingEvent(key.signalType(), key.qualifiers(),
                    receiver.signalType(), qualifiers)) {
                return false;
            }
            if (key.responseType() != null) {
                if (receiver.responseType() == null || !beanContainer.isMatchingEvent(receiver.responseType(), Set.of(),
                        key.responseType(), Set.of())) {
                    return false;
                }
            }
            LOG.infof("Invalidate resolved receivers for: %s", key);
            return true;
        });
    }

    private static Set<Annotation> effectiveQualifiers(Set<Annotation> qualifiers) {
        if (qualifiers.isEmpty()) {
            return Set.of(Any.Literal.INSTANCE, Default.Literal.INSTANCE);
        } else if (!qualifiers.contains(Any.Literal.INSTANCE)) {
            Set<Annotation> effective = new HashSet<>(qualifiers);
            effective.add(Any.Literal.INSTANCE);
            return effective;
        }
        return qualifiers;
    }

    private record SignalResolvable(Type signalType, Set<Annotation> qualifiers, Type responseType) {
    }
}
