package io.quarkiverse.signals.runtime;

import java.util.List;
import java.util.function.Supplier;

import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class SignalsRecorder {

    public Supplier<Object> createContext(List<String> receiversClasses) {
        return new Supplier<Object>() {

            @Override
            public Object get() {
                return new SignalsContext() {

                    @Override
                    public List<String> receiversClasses() {
                        return receiversClasses;
                    }
                };
            }
        };
    }

    public interface SignalsContext {

        List<String> receiversClasses();

    }
}
