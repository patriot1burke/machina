package io.quarkiverse.machina.deployment;

import io.quarkus.builder.item.MultiBuildItem;

public final class MachineBuildItem extends MultiBuildItem {
    Class<?> intf;

    public MachineBuildItem(Class<?> intf) {
        this.intf = intf;
    }

    public Class<?> getIntf() {
        return intf;
    }
}
