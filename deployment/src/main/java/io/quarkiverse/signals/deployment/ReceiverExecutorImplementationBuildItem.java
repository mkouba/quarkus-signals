package io.quarkiverse.signals.deployment;

import java.util.EnumSet;
import java.util.Set;

import io.quarkiverse.signals.spi.Receiver.ExecutionModel;
import io.quarkus.builder.item.SimpleBuildItem;

final class ReceiverExecutorImplementationBuildItem extends SimpleBuildItem {

    private final ReceiverExecutorImplementation implemenation;

    ReceiverExecutorImplementationBuildItem(ReceiverExecutorImplementation implemenation) {
        this.implemenation = implemenation;
    }

    ReceiverExecutorImplementation getImplemenation() {
        return implemenation;
    }

    boolean isSupported(ExecutionModel model) {
        return implemenation.getSupportedModels().contains(model);
    }

    enum ReceiverExecutorImplementation {
        VERTX(EnumSet.allOf(ExecutionModel.class)),
        DEFAULT_BLOCKING(EnumSet.of(ExecutionModel.BLOCKING));

        private Set<ExecutionModel> supportedModels;

        ReceiverExecutorImplementation(Set<ExecutionModel> supportedModels) {
            this.supportedModels = supportedModels;
        }

        Set<ExecutionModel> getSupportedModels() {
            return supportedModels;
        }
    }

}
