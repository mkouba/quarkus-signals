package io.quarkiverse.signals.test;

import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.signals.Receives;
import io.quarkus.test.QuarkusUnitTest;

public class StaticReceiverValidationTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(StaticReceiverBean.class))
            .setExpectedException(IllegalStateException.class);

    @Test
    public void testStaticReceiverMethodFails() {
        // deployment should fail
    }

    @Singleton
    public static class StaticReceiverBean {

        static void onMsg(@Receives String msg) {
        }
    }
}
