package io.quarkiverse.signals.test;

import static org.junit.jupiter.api.Assertions.fail;

import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.signals.spi.ComponentOrder;
import io.quarkiverse.signals.spi.ReceiverInterceptor;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.common.annotation.Identifier;
import io.smallrye.mutiny.Uni;

public class InterceptorNonExistentOrderRefTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(MyInterceptor.class))
            .setExpectedException(IllegalStateException.class, true);

    @Test
    public void testFailure() {
        fail();
    }

    @Identifier("my-interceptor")
    @ComponentOrder(after = "nonexistent")
    @Singleton
    public static class MyInterceptor implements ReceiverInterceptor {

        @Override
        public Uni<Object> intercept(InterceptionContext context) {
            return context.proceed();
        }
    }
}
