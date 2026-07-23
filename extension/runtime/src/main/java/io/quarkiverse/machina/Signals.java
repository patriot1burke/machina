package io.quarkiverse.machina;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Signals {
    final Map<String, List<Object>> signals;

    public Signals() {
        signals = new HashMap<>();
    }

    public Signals(Map<String, List<Object>> signals) {
        this.signals = signals;
    }

    public Signals add(String key, List<Object> value) {
        signals.computeIfAbsent(key, k -> new ArrayList<>()).addAll(value);
        return this;
    }

    public Signals add(String key, Object value) {
        signals.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        return this;
    }

    public Signals addAll(Map<String, List<Object>> inputs) {
        inputs.forEach(this::add);
        return this;
    }

    public Signals addAll(Signals inputs) {
        addAll(inputs.signals);
        return this;
    }

    public Signals add(Object... objects) {
        for (Object obj : objects) {
            add(obj.getClass().getName(), obj);
        }
        return this;
    }

    public List<Object> signal(String signalName) {
        return signals.get(signalName);
    }

    public boolean hasSignal(String signalName) {
        return signals.containsKey(signalName);
    }

    public Set<String> signalNames() {
        return signals.keySet();
    }

    public int numSignals() {
        return signals.size();
    }

    public Map<String, List<Object>> signals() {
        return signals;
    }

    @Override
    public String toString() {
        if (signals.isEmpty())
            return "Signals {}";
        StringBuilder sb = new StringBuilder("Signals {\n");
        signals.forEach((key, values) -> {
            sb.append("  ").append(key).append(":\n");
            for (Object value : values) {
                sb.append("    - ").append(value).append("\n");
            }
        });
        sb.append("}");
        return sb.toString();
    }
}
