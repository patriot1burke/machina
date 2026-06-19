package io.quarkiverse.machina.deployment;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.RequestScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import io.quarkiverse.machina.Gear;
import io.quarkiverse.machina.SignalProducer;
import io.quarkiverse.machina.internal.MachineRecorder.GearSignal;
import io.quarkiverse.machina.internal.MachineRecorder.SignalType;
import io.quarkiverse.machina.internal.SignalUtil;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.logging.Log;

public class GearDeploymentProcessor {
    static Logger log = Logger.getLogger(GearDeploymentProcessor.class);

    public static final DotName GEAR = DotName.createSimple(Gear.class.getName());
    public static final DotName REQUEST_SCOPED = DotName.createSimple(RequestScoped.class.getName());

    @BuildStep
    public void findGears(CombinedIndexBuildItem combinedIndexBuildItem, BuildProducer<GearBuildItem> producer,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            BuildProducer<AdditionalBeanBuildItem> additionalBeanProducer,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeanBuildItemBuildProducer) throws Exception {
        IndexView index = combinedIndexBuildItem.getIndex();
        Collection<AnnotationInstance> funqs = index.getAnnotations(GEAR);
        Set<String> beans = new HashSet<>();
        for (AnnotationInstance funqMethod : funqs) {
            MethodInfo methodInfo = funqMethod.target().asMethod();
            ClassInfo declaringClass = methodInfo.declaringClass();
            String className = declaringClass.name().toString();
            String methodName = methodInfo.name();
            if (Modifier.isAbstract(methodInfo.flags()) && !Modifier.isInterface(declaringClass.flags())) {
                throw new RuntimeException(
                        String.format("Method '%s' annotated with '@Gear' declared in the class '%s' is abstract.",
                                methodName, className));
            }

            if (Modifier.isAbstract(declaringClass.flags()) && !Modifier.isInterface(declaringClass.flags())) {
                throw new RuntimeException(
                        String.format(
                                "@Gear is not allowed within abstract classes. Method '%s' annotated with '@Gear' is declared within the class '%s'.",
                                methodName, className));
            }

            if (!Modifier.isPublic(methodInfo.flags())) {
                throw new RuntimeException(
                        String.format(
                                "Method '%s' annotated with '@Gear' declared in the class '%s' is not public.",
                                methodName, className));
            }
            reflectiveClass.produce(ReflectiveClassBuildItem.builder(className).methods().build());
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Class<?> clazz = cl.loadClass(className);
            beans.add(className);
            Method method = toMethod(methodInfo, clazz);
            Gear gearAnnotation = method.getAnnotation(Gear.class);
            String gearName = gearAnnotation.value().isEmpty() ? method.toGenericString() : gearAnnotation.value();
            List<GearSignal> parameters = parameters(method);
            GearSignal returnSignal = outputSignal(method);
            Log.debug("Found gear " + gearName + " for method " + method.toGenericString());
            producer.produce(new GearBuildItem(gearName, clazz, methodName, returnSignal, parameters));
        }
        if (!beans.isEmpty()) {
            additionalBeanProducer.produce(AdditionalBeanBuildItem.builder().addBeanClasses(beans)
                    .setDefaultScope(REQUEST_SCOPED).setUnremovable().build());
        }
    }

    private static GearSignal outputSignal(Method method) {
        if (method.getReturnType().equals(void.class)) {
            return null;
        }
        SignalType returnSignalType = null;
        String signalName = SignalUtil.toSignalName(method.getGenericReturnType(), method.getAnnotations());
        if (method.getReturnType().equals(List.class)) {
            returnSignalType = SignalType.LIST_RESULT;
        } else {
            returnSignalType = SignalType.OBJECT_RESULT;
        }
        return new GearSignal(returnSignalType, signalName, method.getReturnType());
    }

    private static List<GearSignal> parameters(Method method) {
        log.debug("Signal parameters for method " + method.getName());
        List<GearSignal> signals = new ArrayList<>();
        for (int i = 0; i < method.getParameterCount(); i++) {
            Class<?> clazz = method.getParameterTypes()[i];
            SignalType signalType = null;
            String signalName = SignalUtil.toSignalName(method.getGenericParameterTypes()[i],
                    method.getParameterAnnotations()[i]);
            if (clazz.equals(List.class)) {
                signalType = SignalType.INPUT_LIST;
            } else if (clazz.equals(SignalProducer.class)) {
                signalType = SignalType.PRODUCER;
            } else {
                signalType = SignalType.INPUT;
            }
            log.debug("Parameter " + i + " type " + clazz.getName() + " signal " + signalName);
            signals.add(new GearSignal(signalType, signalName, clazz));
        }
        return signals;
    }

    private static Method toMethod(MethodInfo methodInfo, Class<?> clazz) throws Exception {
        List<Class> paramTypes = new ArrayList<>();
        for (Type type : methodInfo.descriptorParameterTypes()) {
            paramTypes.add(toClass(type, clazz.getClassLoader()));
        }
        return clazz.getDeclaredMethod(methodInfo.name(), paramTypes.toArray(new Class<?>[0]));
    }

    public static Class<?> toClass(Type type, ClassLoader cl) throws Exception {
        switch (type.kind()) {
            case Type.Kind.CLASS:
            case Type.Kind.PARAMETERIZED_TYPE:
                return cl.loadClass(type.name().toString());
            case Type.Kind.PRIMITIVE:
                switch (type.asPrimitiveType().primitive()) {
                    case INT -> {
                        return int.class;
                    }
                    case LONG -> {
                        return long.class;
                    }
                    case FLOAT -> {
                        return float.class;
                    }
                    case DOUBLE -> {
                        return double.class;
                    }
                    case BOOLEAN -> {
                        return boolean.class;
                    }
                    case CHAR -> {
                        return char.class;
                    }
                    case BYTE -> {
                        return byte.class;
                    }
                    case SHORT -> {
                        return short.class;
                    }
                }
                break;
        }
        throw new UnsupportedOperationException("Type not supported for now");
    }
}
