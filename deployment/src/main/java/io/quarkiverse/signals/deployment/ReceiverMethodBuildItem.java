package io.quarkiverse.signals.deployment;

import java.util.List;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;
import org.jboss.jandex.Type;
import org.jboss.jandex.Type.Kind;

import io.quarkiverse.signals.Receiver.ExecutionModel;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.arc.processor.InvokerInfo;
import io.quarkus.builder.item.MultiBuildItem;

final class ReceiverMethodBuildItem extends MultiBuildItem implements Comparable<ReceiverMethodBuildItem> {

    private final ExecutionModel executionModel;
    private final BeanInfo bean;
    private final InvokerInfo invoker;
    private final MethodInfo method;
    private final MethodParameterInfo receiveParam;
    private final List<AnnotationInstance> qualifiers;

    public ReceiverMethodBuildItem(ExecutionModel executionModel, BeanInfo bean,
            InvokerInfo invoker, MethodInfo method, MethodParameterInfo receivesParam, List<AnnotationInstance> qualifiers) {
        this.executionModel = executionModel;
        this.bean = bean;
        this.invoker = invoker;
        this.method = method;
        this.receiveParam = receivesParam;
        this.qualifiers = qualifiers;
    }

    public ExecutionModel getExecutionModel() {
        return executionModel;
    }

    public BeanInfo getBean() {
        return bean;
    }

    public InvokerInfo getInvoker() {
        return invoker;
    }

    public MethodInfo getMethod() {
        return method;
    }

    public MethodParameterInfo getReceiveParam() {
        return receiveParam;
    }

    public Type getSignalType() {
        Type paramType = receiveParam.type();
        if (paramType.kind() == Type.Kind.PARAMETERIZED_TYPE
                && paramType.asParameterizedType().name().equals(DotNames.SIGNAL_CONTEXT)) {
            // Unwrap SignalContext<T> to T
            return paramType.asParameterizedType().arguments().get(0);
        }
        return paramType;
    }

    public Type getResponseType() {
        Type returnType = method.returnType();
        if (returnType.kind() == Kind.VOID) {
            return null;
        } else if (returnType.name().equals(DotNames.UNI)) {
            return returnType.asParameterizedType().arguments().get(0);
        } else {
            return returnType;
        }
    }

    public List<AnnotationInstance> getQualifiers() {
        return qualifiers;
    }

    @Override
    public int compareTo(ReceiverMethodBuildItem other) {
        // TODO compare by declaring class name and method.toString
        return 0;
    }

}
