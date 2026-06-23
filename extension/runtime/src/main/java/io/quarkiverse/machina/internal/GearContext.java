package io.quarkiverse.machina.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.quarkiverse.machina.internal.MachineRecorder.GearEntry;

public class GearContext {
    final Map<String, List<Object>> inputs;
    final Map<String, List<Object>> outputs = new ConcurrentHashMap<>();
    final GearEntry gear;
    final MachineEngine machineEngine;
    Throwable error;

    public GearContext(GearEntry gear, MachineEngine machineEngine) {
        inputs = machineEngine.outputs;
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
