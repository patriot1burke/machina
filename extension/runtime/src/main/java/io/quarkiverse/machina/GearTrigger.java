package io.quarkiverse.machina;

import java.util.Set;

import io.quarkiverse.machina.internal.MachineEngine;

public interface GearTrigger {
    record Result(Signals outputs, Set<String> consumedSignals, Set<String> executedGears) {
    }

    public static Result run(Signals input) {
        return MachineEngine.run(input);
    }
}
