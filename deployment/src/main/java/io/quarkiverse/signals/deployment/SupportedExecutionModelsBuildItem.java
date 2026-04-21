package io.quarkiverse.signals.deployment;

import java.util.Set;

import io.quarkiverse.signals.spi.Receiver.ExecutionModel;
import io.quarkus.builder.item.SimpleBuildItem;

final class SupportedExecutionModelsBuildItem extends SimpleBuildItem {

    private final Set<ExecutionModel> supportedModels;

    SupportedExecutionModelsBuildItem(Set<ExecutionModel> supportedModels) {
        this.supportedModels = supportedModels;
    }

    boolean isSupported(ExecutionModel model) {
        return supportedModels.contains(model);
    }

}
