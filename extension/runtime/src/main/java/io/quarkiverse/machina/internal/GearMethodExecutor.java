package io.quarkiverse.machina.internal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jboss.logging.Logger;

import io.quarkiverse.machina.GearExecutionException;
import io.quarkiverse.machina.SignalProducer;
import io.quarkiverse.machina.internal.MachineRecorder.GearSignal;
import io.quarkus.arc.Arc;

public class GearMethodExecutor implements GearExecutor {
    static Logger log = Logger.getLogger(GearMethodExecutor.class);
    final Class<?> clazz;
    final String methodName;
    final Method method;
    final GearSignal methodResult;
    final List<GearSignal> parameters;

    public GearMethodExecutor(Class<?> clazz, String methodName, GearSignal methodResult, List<GearSignal> parameters) {
        this.clazz = clazz;
        this.methodName = methodName;
        this.methodResult = methodResult;
        this.parameters = parameters;

        Class<?>[] parameterTypes = new Class<?>[parameters.size()];
        for (int i = 0; i < parameters.size(); i++) {
            parameterTypes[i] = parameters.get(i).type();
        }

        try {
            this.method = clazz.getDeclaredMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        if (method.getParameterCount() != parameters.size()) {
            throw new RuntimeException("Expected " + parameters.size() + " parameters for method " + methodName + " but got "
                    + method.getParameterCount());
        }
    }

    @Override
    public void execute(GearContext context) {
        Object target = Arc.container().instance(clazz).get();
        List<Object> params = new ArrayList<>(parameters.size());
        for (GearSignal parameter : parameters) {
            if (parameter.signalType() == MachineRecorder.SignalType.INPUT) {
                List<Object> input = context.input(parameter.signalName());
                if (input.size() != 1) {
                    throw new RuntimeException("Expected only 1 input for " + parameter.signalName() + "for method "
                            + methodName + " but got " + input.size());
                }
                params.add(input.get(0));
            } else if (parameter.signalType() == MachineRecorder.SignalType.INPUT_LIST) {
                params.add(context.input(parameter.signalName()));
            } else if (parameter.signalType() == MachineRecorder.SignalType.PRODUCER) {
                SignalProducer producer = signal -> context.output(parameter.signalName(), signal);
                params.add(producer);
            } else if (parameter.signalType() == MachineRecorder.SignalType.OPTIONAL_INPUT) {
                log.info(methodName + " Optional input: " + parameter.signalName());
                List<Object> input = context.input(parameter.signalName());
                if (input == null) {
                    log.info("No optional input for " + parameter.signalName());
                    params.add(Optional.empty());
                } else if (input.size() > 1) {
                    throw new RuntimeException("Expected only 1 optional input for " + parameter.signalName() + "for method "

                            + methodName + " but got " + input.size());
                } else if (input.size() == 1) {
                    log.info("Optional Input for " + parameter.signalName() + " is " + input.get(0));
                    params.add(Optional.of(input.get(0)));
                } else {
                    log.info("Optional Input for " + parameter.signalName() + " is empty");
                    params.add(Optional.empty());
                }
            } else if (parameter.signalType() == MachineRecorder.SignalType.OPTIONAL_INPUT_LIST) {
                List<Object> input = context.input(parameter.signalName());
                Optional<List<Object>> optional = input == null ? Optional.empty() : Optional.of(input);
                params.add(optional);
            } else {
                throw new RuntimeException(
                        "Unsupported parameter type " + parameter.signalType() + " for method " + methodName);
            }
        }

        try {
            Object value = method.invoke(target, params.toArray());
            if (value == null) {
                return;
            }
            if (methodResult.signalType() == MachineRecorder.SignalType.LIST_RESULT) {
                context.output(methodResult.signalName(), (List) value);
            } else if (methodResult.signalType() == MachineRecorder.SignalType.OBJECT_RESULT) {
                context.output(methodResult.signalName(), value);
            } else {
                throw new RuntimeException(
                        "Unsupported return type " + methodResult.signalType() + " for method " + methodName);
            }
        } catch (InvocationTargetException ex) {
            throw new GearExecutionException(ex.getTargetException());
        } catch (Exception e) {
            throw new RuntimeException("failed method execution: " + methodName, e);
        }
    }
}
