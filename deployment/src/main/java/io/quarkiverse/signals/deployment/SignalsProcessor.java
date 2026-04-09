package io.quarkiverse.signals.deployment;

import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;

import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.enterprise.invoke.Invoker;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.PrimitiveType;
import org.jboss.jandex.Type.Kind;
import org.jboss.jandex.TypeVariable;
import org.jboss.jandex.gizmo2.Jandex2Gizmo;

import io.quarkiverse.signals.Receiver.ExecutionModel;
import io.quarkiverse.signals.Signal;
import io.quarkiverse.signals.runtime.InvokerReceiver;
import io.quarkiverse.signals.runtime.InvokerReceiver.ReceiveInfo;
import io.quarkiverse.signals.runtime.ReceiverManager;
import io.quarkiverse.signals.runtime.SignalBeanCreator;
import io.quarkiverse.signals.runtime.SignalsRecorder;
import io.quarkiverse.signals.runtime.SignalsRecorder.SignalsContext;
import io.quarkiverse.signals.runtime.VertxReceiverExecutor;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanArchiveIndexBuildItem;
import io.quarkus.arc.deployment.BeanRegistrationPhaseBuildItem;
import io.quarkus.arc.deployment.InvokerFactoryBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem.ExtendedBeanConfigurator;
import io.quarkus.arc.processor.BeanInfo;
import io.quarkus.arc.processor.InjectionPointInfo;
import io.quarkus.arc.processor.InvokerBuilder;
import io.quarkus.arc.processor.RuntimeTypeCreator;
import io.quarkus.deployment.GeneratedClassGizmo2Adaptor;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.gizmo2.ClassOutput;
import io.quarkus.gizmo2.Const;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.GenericType;
import io.quarkus.gizmo2.Gizmo;
import io.quarkus.gizmo2.LocalVar;
import io.quarkus.gizmo2.TypeArgument;
import io.quarkus.gizmo2.desc.ConstructorDesc;
import io.quarkus.gizmo2.desc.FieldDesc;
import io.quarkus.gizmo2.desc.MethodDesc;
import io.quarkus.runtime.util.HashUtil;

class SignalsProcessor {

    @BuildStep
    void collectReceivers(BeanRegistrationPhaseBuildItem beanRegistration,
            InvokerFactoryBuildItem invokerFactory, BuildProducer<ReceiverMethodBuildItem> receivers) {

        Set<DotName> knownQualifiers = beanRegistration.getBeanProcessor().getBeanDeployment().getQualifiers().stream()
                .map(ci -> ci.name()).collect(Collectors.toSet());

        for (BeanInfo bean : beanRegistration.getContext().beans().classBeans()
                .filter(b -> b.getTarget().get().asClass().hasAnnotation(DotNames.RECEIVES))) {
            ClassInfo beanClass = bean.getTarget().get().asClass();
            for (MethodInfo method : beanClass.methods()) {
                List<MethodParameterInfo> params = method.parameters();
                if (params.isEmpty()) {
                    continue;
                }
                MethodParameterInfo receiveParam = null;
                for (MethodParameterInfo param : params) {
                    if (param.hasDeclaredAnnotation(DotNames.RECEIVES)) {
                        if (receiveParam != null) {
                            throw new IllegalStateException(
                                    "A receiver method must have exactly one parameter annotated with @Receives: "
                                            + methodDesc(method));
                        }
                        receiveParam = param;
                    }
                }
                if (receiveParam != null) {
                    if (Modifier.isPrivate(method.flags())) {
                        throw new IllegalStateException(
                                "A receiver method must not be private: " + methodDesc(method));
                    }
                    if (Modifier.isStatic(method.flags())) {
                        throw new IllegalStateException(
                                "A receiver method must not be static: " + methodDesc(method));
                    }
                    ExecutionModel executionModel = getExecutionModel(method);
                    InvokerBuilder invokerBuilder = invokerFactory.createInvoker(bean, method)
                            .withInstanceLookup();
                    if (params.size() > 1) {
                        for (MethodParameterInfo param : params) {
                            if (param != receiveParam) {
                                invokerBuilder.withArgumentLookup(param.position());
                            }
                        }
                    }
                    List<AnnotationInstance> qualifiers = new ArrayList<>();
                    for (AnnotationInstance a : receiveParam.declaredAnnotations()) {
                        if (knownQualifiers.contains(a.name())) {
                            qualifiers.add(a);
                        }
                    }
                    receivers.produce(
                            new ReceiverMethodBuildItem(executionModel, bean, invokerBuilder.build(), method, receiveParam,
                                    qualifiers));
                }
            }
        }
    }

    @Record(RUNTIME_INIT)
    @BuildStep
    void generateReceivers(SignalsRecorder recorder,
            BeanRegistrationPhaseBuildItem beanRegistration,
            BeanArchiveIndexBuildItem beanArchiveIndex,
            List<ReceiverMethodBuildItem> receivers,
            BuildProducer<GeneratedClassBuildItem> generatedClasses,
            BuildProducer<GeneratedResourceBuildItem> generatedResources,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans) {

        ClassOutput classOutput = new GeneratedClassGizmo2Adaptor(generatedClasses, generatedResources,
                new Function<String, String>() {
                    @Override
                    public String apply(String generatedClassName) {
                        return generatedClassName.substring(0, generatedClassName.indexOf('_'));
                    }
                });
        Gizmo gizmo = Gizmo.create(classOutput)
                .withDebugInfo(false)
                .withParameters(false);

        Set<AnnotationInstance> allQualifiers = new HashSet<>();
        List<String> receiverClasses = new ArrayList<String>();

        for (ReceiverMethodBuildItem receiver : receivers) {
            String receiverClassName = receiver.getMethod().declaringClass().name()
                    + "_"
                    + receiver.getMethod().name()
                    + "_"
                    + HashUtil.sha256(receiver.getMethod().toString());
            gizmo.class_(receiverClassName, cc -> {
                var receiveParamType = receiver.getReceiveParam().type();
                if (receiveParamType.kind() == Kind.PRIMITIVE) {
                    receiveParamType = PrimitiveType.box(receiveParamType.asPrimitiveType());
                }
                var returnType = receiver.getMethod().returnType();
                if (returnType.kind() != Kind.VOID && returnType.kind() == Kind.PRIMITIVE) {
                    returnType = PrimitiveType.box(returnType.asPrimitiveType());
                }
                cc.extends_(
                        GenericType.ofClass(InvokerReceiver.class,
                                Jandex2Gizmo.typeArgumentOf(receiveParamType),
                                returnType.kind() == Kind.VOID ? TypeArgument.of(Void.class)
                                        : Jandex2Gizmo.typeArgumentOf(returnType)));

                FieldDesc signalTypeField = cc.field("signalType", fc -> {
                    fc.private_();
                    fc.final_();
                    fc.setType(Type.class);
                });
                FieldDesc qualifiersField = cc.field("qualifiers", fc -> {
                    fc.private_();
                    fc.final_();
                    fc.setType(Set.class);
                });
                FieldDesc responseTypeField = cc.field("responseType", fc -> {
                    fc.private_();
                    fc.final_();
                    fc.setType(Type.class);
                });

                cc.constructor(con -> {
                    con.body(bc -> {
                        Expr invoker = bc.new_(receiver.getInvoker().getClassDesc());
                        Expr receiveInfo = bc.new_(ReceiveInfo.class,
                                Const.of(receiver.getReceiveParam().position()),
                                Const.of(receiver.getReceiveParam().type().name().equals(DotNames.SIGNAL_CONTEXT)),
                                Const.of((short) receiver.getMethod().parametersCount()),
                                Const.of(receiver.getMethod().returnType().name().equals(DotNames.UNI)));
                        bc.invokeSpecial(ConstructorDesc.of(InvokerReceiver.class, Invoker.class, ReceiveInfo.class),
                                cc.this_(), invoker, receiveInfo);

                        LocalVar tccl = bc.localVar("tccl", bc.invokeVirtual(
                                MethodDesc.of(Thread.class, "getContextClassLoader", ClassLoader.class), bc.currentThread()));
                        // Signal types
                        RuntimeTypeCreator rttc = RuntimeTypeCreator.of(bc).withTCCL(tccl);
                        bc.set(cc.this_().field(signalTypeField), rttc.create(receiver.getSignalType()));
                        // Qualifiers
                        Expr qualifiersSet = bc.setOf(receiver.getQualifiers(),
                                qualifier -> beanRegistration.getBeanProcessor().getAnnotationLiteralProcessor().create(bc,
                                        beanArchiveIndex.getIndex().getClassByName(qualifier.name()), qualifier));
                        bc.set(cc.this_().field(qualifiersField), qualifiersSet);
                        // Response type
                        var responseType = receiver.getResponseType();
                        if (responseType != null) {
                            bc.set(cc.this_().field(responseTypeField), rttc.create(responseType));
                        } else {
                            bc.set(cc.this_().field(responseTypeField), Const.ofNull(Type.class));
                        }

                        bc.return_();
                    });
                });

                cc.method("executionModel", mc -> {
                    mc.returning(ExecutionModel.class);
                    mc.body(bc -> bc.return_(Const.of(receiver.getExecutionModel())));
                });

                cc.method("signalType", mc -> {
                    mc.returning(Type.class);
                    mc.body(bc -> bc.return_(cc.this_().field(signalTypeField)));
                });

                cc.method("qualifiers", mc -> {
                    mc.returning(Set.class);
                    mc.body(bc -> bc.return_(cc.this_().field(qualifiersField)));
                });

                cc.method("responseType", mc -> {
                    mc.returning(Type.class);
                    mc.body(bc -> bc.return_(cc.this_().field(responseTypeField)));
                });
            });
            receiverClasses.add(receiverClassName);
            allQualifiers.addAll(receiver.getQualifiers());
        }

        syntheticBeans.produce(SyntheticBeanBuildItem.configure(SignalsContext.class)
                .scope(Dependent.class)
                .setRuntimeInit()
                .supplier(recorder.createContext(receiverClasses))
                .done());

        for (InjectionPointInfo ip : beanRegistration.getInjectionPoints()) {
            if (ip.getType().name().equals(DotNames.SIGNAL)) {
                allQualifiers.addAll(ip.getRequiredQualifiers());
            }
        }

        ExtendedBeanConfigurator signalConfigurator = SyntheticBeanBuildItem.configure(Signal.class)
                .addType(ParameterizedType.builder(DotNames.SIGNAL).addArgument(TypeVariable.create("T")).build())
                .scope(Dependent.class)
                .addInjectionPoint(ClassType.create(ReceiverManager.class))
                .addInjectionPoint(ClassType.create(InjectionPoint.class));
        for (AnnotationInstance q : allQualifiers) {
            signalConfigurator.addQualifier(q);
        }
        syntheticBeans.produce(signalConfigurator
                .creator(SignalBeanCreator.class)
                .forceApplicationClass()
                .done());
    }

    @BuildStep
    void registerBeans(BuildProducer<AdditionalBeanBuildItem> beans) {
        beans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClasses(ReceiverManager.class, VertxReceiverExecutor.class)
                .build());
    }

    private static ExecutionModel getExecutionModel(MethodInfo method) {
        if (method.hasDeclaredAnnotation(DotNames.RUN_ON_VIRTUAL_THREAD)) {
            return ExecutionModel.VIRTUAL_THREAD;
        } else if (method.hasDeclaredAnnotation(DotNames.BLOCKING)) {
            return ExecutionModel.WORKER_THREAD;
        } else if (method.hasDeclaredAnnotation(DotNames.NON_BLOCKING)) {
            return ExecutionModel.EVENT_LOOP;
        } else {
            // Now test class-level annotations
            if (method.declaringClass().hasDeclaredAnnotation(DotNames.RUN_ON_VIRTUAL_THREAD)) {
                return ExecutionModel.VIRTUAL_THREAD;
            } else if (method.declaringClass().hasDeclaredAnnotation(DotNames.BLOCKING)) {
                return ExecutionModel.WORKER_THREAD;
            } else if (method.declaringClass().hasDeclaredAnnotation(DotNames.NON_BLOCKING)) {
                return ExecutionModel.EVENT_LOOP;
            }
            return hasBlockingSignature(method) ? ExecutionModel.WORKER_THREAD : ExecutionModel.EVENT_LOOP;
        }
    }

    static boolean hasBlockingSignature(MethodInfo method) {
        switch (method.returnType().kind()) {
            case VOID:
            case CLASS:
            case PRIMITIVE:
            case ARRAY:
                return true;
            case PARAMETERIZED_TYPE:
                // Uni non-blocking
                DotName name = method.returnType().asParameterizedType().name();
                return !name.equals(DotNames.UNI);
            default:
                throw new IllegalStateException(
                        "Unsupported return type:" + methodDesc(method));
        }
    }

    private static String methodDesc(MethodInfo method) {
        StringBuilder builder = new StringBuilder()
                .append(method.declaringClass().name().withoutPackagePrefix())
                .append("#")
                .append(method.name())
                .append('(');
        for (Iterator<org.jboss.jandex.Type> it = method.parameterTypes().iterator(); it.hasNext();) {
            builder.append(it.next());
            if (it.hasNext()) {
                builder.append(", ");
            }
        }
        builder.append(')');
        return builder.toString();
    }
}
