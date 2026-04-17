package io.quarkiverse.signals.it;

import jakarta.inject.Singleton;

import io.quarkiverse.signals.Receives;

@Singleton
public class HelloSignals {

    String hello(@Receives String name) {
        return "Hello " + name + "!";
    }
}
