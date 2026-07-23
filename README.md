* Quarkus Machina

This project was inspired by the Quarkus core build processor, specifically `@BuildStep` (if you're familiar with Quarkus).
The goal is to bring this build pattern of Quarkus and turn it into something that can be used within an application with 
all the bells and whistles of a Quarkus runtime too.

I always thought the Quarkus `@BuildStep` pattern would be useful for building actual applications.  They are cool
because `@BuildStep`s are decoupled from one another.  Each step produces and consumes arbitrary input and output.
Depending on the input and what the desired output is, different build steps will be executed. I think of it as one
big decoupled state machine.

Quarkus Machina makes this pattern available to application code.  There are currently 2 modes:

* Static Mode: You know ahead of time what inputs you have and what your desired outputs are.
* Dynamic Mode: You don't know what inputs you will have at runtime or what the state machine will produce.

Quarkus Machina is made up of a few different components:

* `@Gear`s - these are equivalent to `@BuildStep` methods.
* `@Signal` - this provides a String name for an input or output if it cannot be derived from the class name (or you do not want it derived from the class name).
* `SignalProducer` - these are equivalent to `BuildProducer<>`s in Quarkus.
* `@Machine` - Machines define static pre-defined machines that can be preconfigured at build time.  This annotation is placed on an interface.  Each method of the interface defines the interaction with the state machine.  Parameters
are the desired inputs and the return value is the desired output.
* `GearTrigger` - is a dynamic way to trigger the state machine.  Can take dynamic input, and produces dynamic output.


Defining gears:
```java

record Customer(String name, String address) {}

@ApplicationScoped // can be any CDI scope
public class GearBox {
    @Gear
    public void createCustomer(@Signal("name") String name, @Signal("address") Optional<String> address, SignalProducer<Customer> customerProducer) {
        customerProducer.produce(new Customer(name, address.orElse(null)));
    }
}

```

Inputs can be primitive and `String` types so long as they are annotated with `@Signal`.  The FQN of the class is used to derive the name of the input or output.  
If you do not want the FQN to be used, you can provide a name using the `@Signal` annotation.

`Optional<T>` is supported as an input.  This denote the input as optional.

Defining Machines:
```java

@Machine
public interface CustomerMachine {
    Customer createCustomer(@Signal("name") String name);
}
```

Machines are `@Inject`able.  Invoking on a method will trigger any `@Gear` that matches the provided input and desired output.  
Any in-between states and gears will also be invoked just like the Quarkus build runtime does for build steps.  This static definition
is pre-created at build time.

Dynamic Input:
```java
Signals inputSignals = new Signals();
inputSignals.add("name", "Bob");
Signals outputSignals = GearTrigger.run(inputSignals);
```

When you have arbitrary input you can use `GearTrigger.run()` passing in a `Signals` that defines the dynamic inputs.  
Which gears to execute will be determined at runtime.

Gears run in parallel if possible using a `ManagedExecutor` to execute them.


