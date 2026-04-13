package io.quarkiverse.signals.it;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.jboss.resteasy.reactive.RestPath;

import io.quarkiverse.signals.Signal;
import io.smallrye.mutiny.Uni;

@Path("/signals")
@ApplicationScoped
public class SignalsResource {

    @Inject
    Signal<String> signal;

    @GET
    @Path("{name}")
    public Uni<String> hello(@RestPath String name) {
        return signal.request(name, String.class);
    }
}
