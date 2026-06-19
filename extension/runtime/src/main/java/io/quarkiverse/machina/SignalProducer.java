package io.quarkiverse.machina;

public interface SignalProducer<T> {
    void produce(T signal);
}
