package io.quarkiverse.signals.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import jakarta.enterprise.util.TypeLiteral;

import io.quarkiverse.signals.Receiver;
import io.quarkiverse.signals.Receiver.EmissionType;
import io.quarkiverse.signals.Receiver.SignalContext;
import io.quarkiverse.signals.Signal;
import io.smallrye.mutiny.Uni;

public class SignalImpl<T> implements Signal<T> {

    private final Type signalType;
    private final Set<Annotation> qualifiers;
    private final ReceiverManager manager;

    SignalImpl(Type signalType, Set<Annotation> qualifiers, ReceiverManager manager) {
        this.signalType = signalType;
        this.qualifiers = qualifiers;
        this.manager = manager;
    }

    @Override
    public Signal<T> select(Annotation... qualifiers) {
        Set<Annotation> mergedQualifiers = new HashSet<>(this.qualifiers);
        Collections.addAll(mergedQualifiers, qualifiers);
        return new SignalImpl<>(signalType, mergedQualifiers, manager);
    }

    @Override
    public <U extends T> Signal<U> select(Class<U> subtype, Annotation... qualifiers) {
        Set<Annotation> mergedQualifiers = new HashSet<>(this.qualifiers);
        Collections.addAll(mergedQualifiers, qualifiers);
        return new SignalImpl<>(subtype, mergedQualifiers, manager);
    }

    @Override
    public <U extends T> Signal<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
        Set<Annotation> mergedQualifiers = new HashSet<>(this.qualifiers);
        Collections.addAll(mergedQualifiers, qualifiers);
        return new SignalImpl<>(subtype.getType(), mergedQualifiers, manager);
    }

    @Override
    public Unicast<T> unicast() {
        return new UnicastImpl<>();
    }

    @Override
    public Multicast<T> multicast() {
        return new MulticastImpl<>();
    }

    class UnicastImpl<SIGNAL> extends MetadataEmission<UnicastImpl<SIGNAL>> implements Unicast<SIGNAL>, Consumer<Void> {

        @Override
        public Uni<Void> emit(SIGNAL signal) {
            var receiver = manager.nextReceiver(signalType, qualifiers, null);
            if (receiver != null) {
                var signalContext = new SignalContextImpl<>(signal, this.meta == null ? Map.of() : Map.copyOf(this.meta),
                        EmissionType.SEND);
                return manager.receiverExecutor()
                        .execute(cast(receiver), signalContext)
                        .replaceWithVoid();
            } else {
                return Uni.createFrom().voidItem();
            }
        }

        @Override
        public void emitAndForget(SIGNAL signal) {
            emit(signal).subscribe().with(this);
        }

        @Override
        public <RESPONSE> Request<SIGNAL, RESPONSE> request(Class<RESPONSE> responseType) {
            return new RequestImpl<>(meta, responseType);
        }

        @Override
        public <RESPONSE> Request<SIGNAL, RESPONSE> request(TypeLiteral<RESPONSE> responseType) {
            return new RequestImpl<>(meta, responseType.getType());
        }

        @Override
        public void accept(Void v) {
            // noop
        }

    }

    class RequestImpl<SIGNAL, RESPONSE> extends MetadataEmission<RequestImpl<SIGNAL, RESPONSE>>
            implements Request<SIGNAL, RESPONSE> {

        private final Type responseType;

        public RequestImpl(Map<String, Object> meta, Type responseType) {
            super(meta != null ? new HashMap<>(meta) : null);
            this.responseType = responseType;
        }

        @Override
        public Uni<RESPONSE> emit(SIGNAL signal) {
            var signalContext = new SignalContextImpl<>(signal, this.meta == null ? Map.of() : Map.copyOf(this.meta),
                    EmissionType.REQUEST, responseType);
            var receiver = manager.nextReceiver(signalType, qualifiers, responseType);
            if (receiver != null) {
                return cast(manager.receiverExecutor().execute(cast(receiver), signalContext));
            } else {
                return Uni.createFrom().nullItem();
            }
        }

    }

    class MulticastImpl<SIGNAL> extends MetadataEmission<MulticastImpl<SIGNAL>> implements Multicast<SIGNAL>, Consumer<Void> {

        @Override
        public Uni<Void> emit(SIGNAL signal) {
            List<Receiver<?, ?>> receivers = manager.resolveReceivers(signalType, qualifiers);
            if (receivers.isEmpty()) {
                return Uni.createFrom().voidItem();
            }
            var signalContext = new SignalContextImpl<>(signal, this.meta == null ? Map.of() : Map.copyOf(this.meta),
                    EmissionType.PUBLISH);
            List<Uni<Object>> unis = new ArrayList<>(receivers.size());
            for (Receiver<?, ?> receiver : receivers) {
                unis.add(manager.receiverExecutor().execute(cast(receiver), signalContext));
            }
            return Uni.join().all(unis).andCollectFailures().replaceWithVoid();
        }

        @Override
        public void accept(Void v) {
            // noop
        }

        @Override
        public void emitAndForget(SIGNAL signal) {
            emit(signal).subscribe().with(this);
        }

    }

    abstract class MetadataEmission<E extends MetadataEmission<E>> {

        protected Map<String, Object> meta = null;

        protected MetadataEmission() {
            this(null);
        }

        protected MetadataEmission(Map<String, Object> meta) {
            this.meta = meta;
        }

        public E withMeta(String key, Object value) {
            if (meta == null) {
                meta = new HashMap<>();
            }
            meta.put(key, value);
            return self();
        }

        @SuppressWarnings("unchecked")
        private E self() {
            return (E) this;
        }

    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object obj) {
        return (T) obj;
    }

    class SignalContextImpl<SIGNAL> implements SignalContext<SIGNAL> {

        private final SIGNAL signal;
        private final Map<String, Object> meta;
        private final EmissionType emissionType;
        private final Type responseType;

        SignalContextImpl(SIGNAL signal, Map<String, Object> meta, EmissionType emissionType) {
            this(signal, meta, emissionType, null);
        }

        SignalContextImpl(SIGNAL signal, Map<String, Object> meta, EmissionType emissionType, Type responseType) {
            this.signal = signal;
            this.meta = meta;
            this.emissionType = emissionType;
            this.responseType = responseType;
        }

        @Override
        public Map<String, Object> meta() {
            return meta;
        }

        @Override
        public SIGNAL signal() {
            return signal;
        }

        @Override
        public Type signalType() {
            return signalType;
        }

        @Override
        public Type responseType() {
            return responseType;
        }

        @Override
        public Set<Annotation> qualifiers() {
            return qualifiers;
        }

        @Override
        public EmissionType emissionType() {
            return emissionType;
        }

        @Override
        public String toString() {
            return "SignalContextImpl [signal=" + signal + ", emissionType=" + emissionType + ", responseType=" + responseType
                    + "]";
        }

    }

}
