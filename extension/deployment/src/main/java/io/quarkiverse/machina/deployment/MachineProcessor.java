package io.quarkiverse.machina.deployment;

import static io.quarkus.deployment.annotations.ExecutionTime.STATIC_INIT;

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.RequestScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import io.quarkiverse.machina.Machine;
import io.quarkiverse.machina.internal.MachineRecorder;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.logging.Log;
import io.quarkus.runtime.RuntimeValue;

public class MachineProcessor {
    static Logger log = Logger.getLogger(MachineProcessor.class);

    public static final DotName SIGNAL_PROCESSOR = DotName.createSimple(Machine.class.getName());
    public static final DotName REQUEST_SCOPED = DotName.createSimple(RequestScoped.class.getName());

    @BuildStep
    public void findMachines(CombinedIndexBuildItem combinedIndexBuildItem, BuildProducer<MachineBuildItem> producer,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            BuildProducer<AdditionalBeanBuildItem> additionalBeanProducer,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeanBuildItemBuildProducer) throws Exception {
        IndexView index = combinedIndexBuildItem.getIndex();
        Collection<AnnotationInstance> processors = index.getAnnotations(SIGNAL_PROCESSOR);
        Set<String> beans = new HashSet<>();
        for (AnnotationInstance processor : processors) {
            ClassInfo declaringClass = processor.target().asClass();
            String className = declaringClass.name().toString();
            if (!Modifier.isInterface(declaringClass.flags())) {
                throw new RuntimeException(
                        String.format("Class '%s' annotated with '@SignalProcessor' must be an interface.",
                                className));
            }
            reflectiveClass.produce(ReflectiveClassBuildItem.builder(className).methods().build());
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Class<?> clazz = cl.loadClass(className);
            beans.add(className);
            Log.debug("Found signal processor " + className);
            producer.produce(new MachineBuildItem(clazz));
            // todo figure out all processors from methods at deployment instead of static init
        }
        if (!beans.isEmpty()) {
            additionalBeanProducer.produce(AdditionalBeanBuildItem.builder().addBeanClasses(beans)
                    .setDefaultScope(REQUEST_SCOPED).setUnremovable().build());
        }
    }

    @BuildStep
    @Record(STATIC_INIT)
    public void build(List<GearBuildItem> gears, MachineRecorder recorder,
            List<MachineBuildItem> processors,
            BuildProducer<SyntheticBeanBuildItem> synthetics) {
        for (GearBuildItem gear : gears) {
            recorder.register(gear.getName(), gear.getClazz(), gear.getMethod(), gear.getMethodResult(),
                    gear.getParameters());
        }
        for (MachineBuildItem processor : processors) {
            RuntimeValue value = recorder.createMachine(processor.getIntf());
            synthetics.produce(SyntheticBeanBuildItem.configure(processor.getIntf()).scope(RequestScoped.class)
                    .runtimeValue(value).unremovable().done());
        }

    }
}
