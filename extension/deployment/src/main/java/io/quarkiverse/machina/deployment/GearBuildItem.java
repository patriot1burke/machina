package io.quarkiverse.machina.deployment;

import java.util.List;

import io.quarkiverse.machina.internal.MachineRecorder.GearSignal;
import io.quarkus.builder.item.MultiBuildItem;

public final class GearBuildItem extends MultiBuildItem {
    String name;
    Class<?> clazz;
    String method;
    GearSignal methodResult;
    List<GearSignal> parameters;

    public GearBuildItem() {
    }

    public GearBuildItem(String name, Class<?> clazz, String method, GearSignal methodResult,
            List<GearSignal> parameters) {
        this.name = name;
        this.clazz = clazz;
        this.method = method;
        this.methodResult = methodResult;
        this.parameters = parameters;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Class<?> getClazz() {
        return clazz;
    }

    public void setClazz(Class<?> clazz) {
        this.clazz = clazz;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public GearSignal getMethodResult() {
        return methodResult;
    }

    public void setMethodResult(GearSignal methodResult) {
        this.methodResult = methodResult;
    }

    public List<GearSignal> getParameters() {
        return parameters;
    }

    public void setParameters(List<GearSignal> parameters) {
        this.parameters = parameters;
    }
}
