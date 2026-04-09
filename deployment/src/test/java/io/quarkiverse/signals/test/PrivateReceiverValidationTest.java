package io.quarkiverse.signals.test;

import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.signals.Receives;
import io.quarkus.test.QuarkusUnitTest;

public class PrivateReceiverValidationTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(PrivateReceiverBean.class))
            .setExpectedException(IllegalStateException.class);

    @Test
    public void testPrivateReceiverMethodFails() {
        // deployment should fail
    }

    @Singleton
    public static class PrivateReceiverBean {

        private void onMsg(@Receives String msg) {
        }
    }

}
