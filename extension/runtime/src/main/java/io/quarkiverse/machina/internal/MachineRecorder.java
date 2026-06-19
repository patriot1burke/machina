package io.quarkiverse.machina.internal;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jboss.logging.Logger;

import io.quarkiverse.machina.OutputClasses;
import io.quarkiverse.machina.OutputSignals;
import io.quarkiverse.machina.ProcessorResult;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class MachineRecorder {
    static Logger log = Logger.getLogger(MachineRecorder.class);

    record GearEntry(String name, Set<String> inputs, Set<String> outputs, GearExecutor executor) {
        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            GearEntry gearEntry = (GearEntry) o;
            return Objects.equals(name, gearEntry.name);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static void registerGear(GearEntry entry) {
        log.info("Registering gear: " + entry.name() + " (inputs: " + entry.inputs() + ", outputs: " + entry.outputs() + ")");
        for (String output : entry.outputs) {
            gearOutputs.computeIfAbsent(output, k -> new HashSet<>()).add(entry);
        }
    }

    // what gears produce what outputs
    static Map<String, Set<GearEntry>> gearOutputs = new HashMap<>();

    public record MachineDefinition(Set<String> desiredOutputs, List<String> inputs, Set<GearEntry> gearEntries,
            Map<String, Set<GearEntry>> gearInputs, Map<String, Set<GearEntry>> gearOutputs, Set<String> unresolvable) {

    }

    public static MachineDefinition generateMachineDefinition(String... requestedOutputs) {
        log.info("Generating machine for outputs: " + Arrays.toString(requestedOutputs));
        if (gearOutputs.isEmpty()) {
            log.info("Gear outputs: (none registered)");
        } else {
            log.info("Gear outputs:");
            gearOutputs.forEach((signal, gears) -> {
                log.info("  " + signal + ":");
                gears.forEach(
                        n -> log.info("    -> " + n.name() + " (inputs: " + n.inputs() + ", outputs: " + n.outputs() + ")"));
            });
        }

        Set<GearEntry> gearEntries = new HashSet<>();
        Map<String, Set<GearEntry>> gearInputs = new HashMap<>();
        Map<String, Set<GearEntry>> gearOutputs = new HashMap<>();

        Set<String> outputs = new HashSet<>();
        Set<String> unresolvable = new HashSet<>();
        Set<String> desiredOutputs = new HashSet<>();
        for (String output : requestedOutputs) {
            stepsFor(output, gearEntries, gearInputs, outputs, unresolvable);
            desiredOutputs.add(output);
        }
        gearEntries.forEach(gear -> {
            gear.outputs.forEach(output -> gearOutputs.computeIfAbsent(output, k -> new HashSet<>()).add(gear));
        });
        return new MachineDefinition(desiredOutputs, new ArrayList<>(), gearEntries, gearInputs, gearOutputs,
                unresolvable);
    }

    static void stepsFor(String output, Set<GearEntry> entries, Map<String, Set<GearEntry>> inputs,
            Set<String> outputs, Set<String> unresolvable) {
        if (outputs.contains(output)) {
            return;
        }
        outputs.add(output);
        Set<GearEntry> gears = gearOutputs.get(output);
        if (gears == null) {
            unresolvable.add(output);
            return;
        }
        for (GearEntry gear : gears) {
            entries.add(gear);
            for (String input : gear.inputs) {
                inputs.computeIfAbsent(input, k -> new HashSet<>()).add(gear);
                stepsFor(input, entries, inputs, outputs, unresolvable);
            }
        }
    }

    public enum SignalType {
        INPUT,
        INPUT_LIST,
        PRODUCER,
        LIST_RESULT,
        OBJECT_RESULT
    }

    public record GearSignal(SignalType signalType, String signalName, Class<?> type) {

    }

    public void register(String gearName, Class<?> clazz, String method, GearSignal methodResult,
            List<GearSignal> parameters) {
        Set<String> inputs = new HashSet<>();
        Set<String> outputs = new HashSet<>();
        for (GearSignal parameter : parameters) {
            if (parameter.signalType() == SignalType.INPUT || parameter.signalType() == SignalType.INPUT_LIST) {
                inputs.add(parameter.signalName());
            } else if (parameter.signalType() == SignalType.PRODUCER) {
                outputs.add(parameter.signalName());
            }
        }
        if (methodResult != null) {
            outputs.add(methodResult.signalName());
        }
        GearMethodExecutor executor = new GearMethodExecutor(clazz, method, methodResult, parameters);
        registerGear(new GearEntry(gearName, inputs, outputs, executor));
    }

    public <T> RuntimeValue<T> createMachine(Class<T> intf) {
        Map<String, MachineDefinition> methods = new HashMap<>();
        for (Method method : intf.getMethods()) {
            if (method.isDefault()) {
                continue;
            }
            OutputClasses outputClasses = method.getAnnotation(OutputClasses.class);
            OutputSignals outputSignals = method.getAnnotation(OutputSignals.class);
            if (outputClasses == null && outputSignals == null
                    && (method.getReturnType().equals(ProcessorResult.class) || method.getReturnType().equals(void.class))) {
                throw new RuntimeException(
                        "Method " + method.toGenericString() + " must define outputs.  Use @OutputClasses or @OutputSignals");
            }
            List<String> outputs = new ArrayList<>();
            if (outputClasses != null) {
                for (Class<?> clazz : outputClasses.value()) {
                    outputs.add(clazz.getName());
                }
            }
            if (outputSignals != null) {
                for (String signal : outputSignals.value()) {
                    outputs.add(signal);
                }
            }
            if (outputClasses == null && outputSignals == null) {
                String signalname = SignalUtil.toSignalName(method.getGenericReturnType(), method.getAnnotations());
                outputs.add(signalname);
            }
            MachineDefinition def = generateMachineDefinition(outputs.toArray(new String[outputs.size()]));
            for (int i = 0; i < method.getParameterCount(); i++) {
                String signalName = SignalUtil.toSignalName(method.getGenericParameterTypes()[i],
                        method.getParameterAnnotations()[i]);
                def.inputs().add(signalName);
            }
            methods.put(method.toGenericString(), def);
        }
        return new RuntimeValue<>((T) Proxy.newProxyInstance(intf.getClassLoader(), new Class[] { intf },
                new ReflectiveMachineProxy(intf, methods)));
    }
}
