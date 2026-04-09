package io.quarkiverse.signals.test;

import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.signals.Receives;
import io.quarkus.test.QuarkusUnitTest;

public class MultipleReceivesValidationTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest()
            .withApplicationRoot(root -> root.addClasses(MultipleReceivesBean.class))
            .setExpectedException(IllegalStateException.class);

    @Test
    public void testMultipleReceivesParamsFails() {
        // deployment should fail
    }

    @Singleton
    public static class MultipleReceivesBean {

        void onMsg(@Receives String first, @Receives String second) {
        }
    }
}
