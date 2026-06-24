package io.quarkiverse.machina.internal;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.eclipse.microprofile.context.ManagedExecutor;

import io.quarkiverse.machina.OutputSignals;
import io.quarkiverse.machina.ProcessorResult;
import io.quarkiverse.machina.Signals;
import io.quarkiverse.machina.internal.MachineRecorder.MachineDefinition;
import io.quarkus.arc.Arc;
import io.quarkus.arc.impl.LazyValue;

public class ReflectiveMachineProxy implements InvocationHandler {

    Map<String, List<BiConsumer<Signals, Object>>> params = new java.util.HashMap<>();
    Map<String, MachineDefinition> methods;
    Map<String, String> resultMap = new java.util.HashMap<>();
    LazyValue<ManagedExecutor> managedExecutor = new LazyValue<>(() -> {
        return Arc.container().instance(ManagedExecutor.class).get();
    });

    public ReflectiveMachineProxy(Class<?> intf, Map<String, MachineDefinition> methods) {
        methods.forEach((name, def) -> {
            for (String input : def.inputs()) {
                params.computeIfAbsent(name, k -> new ArrayList<>()).add((m, v) -> {
                    if (v == null) {
                        return;
                    }
                    if (v instanceof Signals signals) {
                        m.addAll(signals);
                    } else if (v instanceof List list) {
                        m.add(input, list);
                    } else {
                        m.add(input, v);
                    }
                });
            }
        });
        this.methods = methods;
    }

    private static String getSignalName(AnnotatedElement annotatedElement, Class targetClass, Type targetType) {
        String signalName = null;
        OutputSignals outputSignals = annotatedElement.getAnnotation(OutputSignals.class);
        if (outputSignals != null) {
            signalName = outputSignals.value()[0];
        } else if (targetClass == List.class) {
            if (targetType instanceof ParameterizedType pt) {
                signalName = pt.getActualTypeArguments()[0].toString();
            } else {
                throw new RuntimeException("List return type must be parameterized");
            }
        } else {
            signalName = targetType.toString();
        }
        return signalName;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.isDefault()) {
            return InvocationHandler.invokeDefault(proxy, method, args);
        }
        String genericString = method.toGenericString();
        List<BiConsumer<Signals, Object>> consumers = params.get(genericString);
        MachineDefinition machineDefinition = methods.get(genericString);
        if (consumers == null) {
            throw new RuntimeException("No consumer found for " + genericString);
        }
        Signals inputs = new Signals();
        for (int i = 0; i < args.length; i++) {
            consumers.get(i).accept(inputs, args[i]);
        }
        Signals outputs = MachineEngine.runDefinition(machineDefinition, inputs);
        if (method.getReturnType() == ProcessorResult.class) {
            return (ProcessorResult) () -> outputs;
        } else if (method.getReturnType() == void.class) {
            return null;
        } else {
            String signalName = machineDefinition.desiredOutputs().iterator().next();
            System.out.println("returning signalName: " + signalName);
            List<Object> result = outputs.signals().get(signalName);
            if (method.getReturnType().equals(List.class)) {
                return result;
            } else if (result.size() == 1) {
                return result.get(0);
            } else {
                throw new RuntimeException("Expected 1 result for " + signalName + " but got " + result.size());
            }

        }
    }
}
