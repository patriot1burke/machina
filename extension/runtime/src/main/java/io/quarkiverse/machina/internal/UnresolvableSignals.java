package io.quarkiverse.machina.internal;

import java.util.Set;

public class UnresolvableSignals extends RuntimeException {
    final Set<String> signals;

    public UnresolvableSignals(Set<String> signals) {
        super("Unresolvable signals: " + signals);
        this.signals = signals;
    }

    public Set<String> getSignals() {
        return signals;
    }
}
