package io.quarkiverse.machina.internal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.logging.Logger;

import io.quarkiverse.machina.GearExecutionException;
import io.quarkiverse.machina.Signals;
import io.quarkiverse.machina.internal.MachineRecorder.GearEntry;
import io.quarkiverse.machina.internal.MachineRecorder.MachineDefinition;

public class MachineEngine {
    static Logger log = Logger.getLogger(MachineEngine.class);
    ManagedExecutor managedExecutor;

    MachineDefinition machineDefinition;
    Map<String, List<Object>> outputs = new ConcurrentHashMap<>();
    Map<String, Set<GearEntry>> producers = new ConcurrentHashMap<>();
    Map<String, List<Object>> gatheredOutputs = new ConcurrentHashMap<>();
    BlockingQueue<GearContext> queue = new LinkedBlockingQueue<>();
    Set<GearEntry> gears = new HashSet<>();

    public MachineEngine(ManagedExecutor managedExecutor, MachineDefinition machineDefinition) {
        this.managedExecutor = managedExecutor;
        this.machineDefinition = machineDefinition;
    }

    public Signals execute(Signals inputs) {

        System.out.println("Executing engine with inputs: \n" + inputs);
        outputs.putAll(inputs.signals());
        machineDefinition.gearOutputs().forEach((k, v) -> {
            producers.computeIfAbsent(k, k1 -> new HashSet<>()).addAll(v);
        });
        System.out.println("Number of gears: " + machineDefinition.gearEntries().size());
        System.out.println("Gears: " + machineDefinition.gearEntries());
        System.out.println("Desired outputs: " + machineDefinition.desiredOutputs());
        gears.addAll(machineDefinition.gearEntries());
        Set<GearEntry> readyGears = readyGears();
        if (readyGears.isEmpty()) {
            throw new RuntimeException("No initial gears are ready to fire that resolve outputs");
        }

        try {
            loop(readyGears);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        Signals result = new Signals();
        machineDefinition.desiredOutputs().forEach(k -> {
            if (outputs.containsKey(k))
                result.add(k, outputs.get(k));
        });
        return result;
    }

    void executeGear(GearEntry gear) {
        GearContext context = new GearContext(gear, this);
        try {
            gear.executor().execute(context);
        } catch (Exception e) {
            context.error(e);
        }
        queue.add(context);
    }

    void loop(Set<GearEntry> initial) throws InterruptedException {
        int numGears = gears.size();
        for (GearEntry gear : initial) {
            gears.remove(gear);
            managedExecutor.execute(() -> executeGear(gear));
        }

        do {
            GearContext context = queue.take();
            if (context.error != null) {
                throw new GearExecutionException(context.error);
            }
            System.out.println("Gears finished: " + context.gear.name());

            for (Map.Entry<String, List<Object>> entry : context.outputs.entrySet()) {
                System.out.println("\toutputted: " + entry.getKey());
                gatheredOutputs.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).addAll(entry.getValue());
                Set<GearEntry> producerOutputs = producers.get(entry.getKey());
                System.out.println("producerOutputs before: " + producerOutputs.size());
                producerOutputs.remove(context.gear);
                System.out.println("producerOutputs after: " + producerOutputs.size());
                if (producerOutputs.isEmpty()) {
                    outputs.put(entry.getKey(), gatheredOutputs.get(entry.getKey()));
                }
            }
            numGears--;
            System.out.println("Gears left: " + numGears);
            if (numGears == 0) {
                return;
            }
            Set<GearEntry> readyGears = readyGears();
            if (readyGears.isEmpty()) {
                continue;
            }
            for (GearEntry gear : readyGears) {
                gears.remove(gear);
                managedExecutor.execute(() -> executeGear(gear));
            }

        } while (true);

    }

    Set<GearEntry> readyGears() {
        System.out.println("Gears ready:");
        System.out.println("\tOutputs:");

        for (String output : outputs.keySet()) {
            System.out.println("\t" + output);
        }
        System.out.println("\tMatching:");
        Set<GearEntry> ready = gears.stream()
                .filter(entry -> {
                    System.out.println(
                            "\t\t" + entry.name() + " inputs " + entry.inputs() + " optional " + entry.optionalInputs());
                    if (!entry.inputs().stream().allMatch(outputs::containsKey)) {
                        return false;
                    }
                    for (String optional : entry.optionalInputs()) {
                        if (!outputs.containsKey(optional)) {
                            System.out.println("\t\toptional Input " + optional + " not in outputs");
                            if (machineDefinition.gearOutputs().containsKey(optional)) {
                                System.out.println("\t\t\tbut is in machine outputs");
                                return false;
                            } else {
                                System.out.println("\t\t\tand is not in machine outputs");
                            }
                        }
                    }
                    return true;
                })
                .collect(Collectors.toSet());
        System.out.println("\tMatched: " + ready);
        return ready;
    }
}
