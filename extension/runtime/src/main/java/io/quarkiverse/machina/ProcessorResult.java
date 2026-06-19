package io.quarkiverse.machina;

import java.util.List;

public interface ProcessorResult {

    default <T> List<T> output(String input) {
        return (List<T>) outputs().signals().get(input);
    }

    default <T> List<T> output(Class<T> clazz) {
        return (List<T>) outputs().signals().get(clazz.getName());
    }

    Signals outputs();
}
