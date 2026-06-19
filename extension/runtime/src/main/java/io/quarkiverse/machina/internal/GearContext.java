package io.quarkiverse.machina.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.quarkiverse.machina.internal.MachineRecorder.GearEntry;

public class GearContext {
    final Map<String, List<Object>> inputs = new HashMap<>();
    final Map<String, List<Object>> outputs = new ConcurrentHashMap<>();
    final GearEntry gear;
    final MachineEngine machineEngine;
    Throwable error;

    public GearContext(GearEntry gear, MachineEngine machineEngine) {
        for (String input : gear.inputs()) {
            inputs.put(input, machineEngine.outputs.get(input));
        }
        this.gear = gear;
        this.machineEngine = machineEngine;
    }

    public List<Object> input(String key) {
        return inputs.get(key);
    }

    public void output(String key, Object value) {
        outputs.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
    }

    public void output(String key, List<Object> value) {
        outputs.computeIfAbsent(key, k -> new ArrayList<>()).addAll(value);
    }

    public void error(Throwable error) {
        this.error = error;
    }
}
