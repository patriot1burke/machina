package io.quarkiverse.machina.internal;

@FunctionalInterface
public interface GearExecutor {
    void execute(GearContext context);
}
