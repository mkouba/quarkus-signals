package io.quarkiverse.signals.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.BeanContainer;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.signals.Receivers;
import io.quarkiverse.signals.SignalContext;
import io.quarkiverse.signals.runtime.ReceiverDefinitionImpl.CallbackReceiver;
import io.quarkiverse.signals.runtime.SignalsRecorder.SignalsContext;
import io.quarkiverse.signals.spi.Receiver;
import io.quarkiverse.signals.spi.ReceiverInterceptor;
import io.quarkiverse.signals.spi.SignalMetadataEnricher;
import io.quarkus.arc.All;
import io.quarkus.arc.Unremovable;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.mutiny.Uni;

@Unremovable
@Singleton
public class ReceiverManager implements Receivers {

    private static final Logger LOG = Logger.getLogger(ReceiverManager.class);

    private final ConcurrentMap<String, Receiver<?, ?>> receivers;

    private final ConcurrentMap<SignalResolvable, RoundRobin<Receiver<?, ?>>> resolvedReceivers;

    private final ReceiverExecutor executor;

    private final BeanContainer beanContainer;

    private final List<SignalMetadataEnricher> enrichers;

    private final List<ReceiverInterceptor> interceptors;

    ReceiverManager(SignalsContext signalsContext,
            ReceiverExecutor executor,
            @All List<SignalMetadataEnricher> allEnrichers,
            @All List<ReceiverInterceptor> allInterceptors,
            BeanContainer beanContainer) {
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
        this.beanContainer = beanContainer;
        this.enrichers = orderByIdentifier(allEnrichers, signalsContext.orderedEnricherIds());
        this.interceptors = orderByIdentifier(allInterceptors, signalsContext.orderedInterceptorIds());
    }

    List<SignalMetadataEnricher> enrichers() {
        return enrichers;
    }

    <SIGNAL, RESPONSE> Uni<RESPONSE> executeReceiver(Receiver<SIGNAL, RESPONSE> receiver,
            SignalContext<SIGNAL> signalContext) {
        if (interceptors.isEmpty()) {
            return executor.execute(receiver, signalContext);
        }
        return executor.execute(new InterceptedReceiver<>(receiver, interceptors), signalContext);
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
        LOG.debugf("Computed %s receivers for: %s", matching.size(), signalResolvable);
        return new RoundRobin<>(matching);
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
        if (!executor.supportsExecutionModel(receiver.executionModel())) {
            throw new IllegalStateException(
                    "%s not supported by %s".formatted(receiver.executionModel(), executor.getClass().getName()));
        }
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
            LOG.debugf("Invalidate resolved receivers for: %s", key);
            return true;
        });
    }

    private static Set<Annotation> effectiveQualifiers(Set<Annotation> qualifiers) {
        if (qualifiers.isEmpty()) {
            return Set.of(Default.Literal.INSTANCE);
        }
        return qualifiers;
    }

    private record SignalResolvable(Type signalType, Set<Annotation> qualifiers, Type responseType) {
    }

    private static <T> List<T> orderByIdentifier(List<T> instances, List<String> orderedIds) {
        if (instances.isEmpty() || orderedIds.isEmpty()) {
            return List.copyOf(instances);
        }
        Map<String, T> byId = new HashMap<>();
        for (T instance : instances) {
            Identifier identifier = instance.getClass().getAnnotation(Identifier.class);
            if (identifier != null) {
                byId.put(identifier.value(), instance);
            }
        }
        List<T> ordered = new ArrayList<>(instances.size());
        for (String id : orderedIds) {
            T instance = byId.get(id);
            if (instance != null) {
                ordered.add(instance);
            }
        }
        return List.copyOf(ordered);
    }

    private static class InterceptedReceiver<SIGNAL, RESPONSE> implements Receiver<SIGNAL, RESPONSE> {

        private final Receiver<SIGNAL, RESPONSE> delegate;
        private final List<ReceiverInterceptor> interceptors;

        InterceptedReceiver(Receiver<SIGNAL, RESPONSE> delegate, List<ReceiverInterceptor> interceptors) {
            this.delegate = delegate;
            this.interceptors = interceptors;
        }

        @Override
        public java.lang.reflect.Type signalType() {
            return delegate.signalType();
        }

        @Override
        public Set<Annotation> qualifiers() {
            return delegate.qualifiers();
        }

        @Override
        public java.lang.reflect.Type responseType() {
            return delegate.responseType();
        }

        @Override
        public ExecutionModel executionModel() {
            return delegate.executionModel();
        }

        @Override
        public Uni<RESPONSE> notify(SignalContext<SIGNAL> context) {
            return cast(new InterceptorChain(interceptors, delegate, context).proceed());
        }

        @Override
        public String toString() {
            return delegate.toString();
        }

        @SuppressWarnings("unchecked")
        private static <T> T cast(Object obj) {
            return (T) obj;
        }
    }

    private static class InterceptorChain implements ReceiverInterceptor.InterceptionContext {

        private final List<ReceiverInterceptor> interceptors;
        private final Receiver<?, ?> receiver;
        private final SignalContext<?> signalContext;
        private int index;

        InterceptorChain(List<ReceiverInterceptor> interceptors, Receiver<?, ?> receiver,
                SignalContext<?> signalContext) {
            this.interceptors = interceptors;
            this.receiver = receiver;
            this.signalContext = signalContext;
            this.index = 0;
        }

        @Override
        public Receiver<?, ?> receiver() {
            return receiver;
        }

        @Override
        public SignalContext<?> signalContext() {
            return signalContext;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Uni<Object> proceed() {
            if (index < interceptors.size()) {
                return interceptors.get(index++).intercept(this);
            }
            return (Uni<Object>) (Uni<?>) receiver.notify(cast(signalContext));
        }

        @SuppressWarnings("unchecked")
        private static <T> T cast(Object obj) {
            return (T) obj;
        }
    }
}
