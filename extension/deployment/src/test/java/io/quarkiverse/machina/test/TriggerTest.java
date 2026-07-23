package io.quarkiverse.machina.test;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.machina.Gear;
import io.quarkiverse.machina.GearTrigger;
import io.quarkiverse.machina.Signal;
import io.quarkiverse.machina.Signals;
import io.quarkus.test.QuarkusUnitTest;

public class TriggerTest {
    static Logger log = Logger.getLogger(TriggerTest.class);
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(Customer.class, Product.class, Order.class,
                            GearBunch.class));

    public record Customer(String name, String address) {
    }

    public record Product(String name, int price) {
    }

    public record Order(List<Product> product, Customer customer) {
    }

    @ApplicationScoped
    public static class GearBunch {
        @Gear("order-gen")
        public Order createOrder(List<Product> products, Customer customer) {
            log.info("Creating order");
            return new Order(products, customer);
        }

        @Gear("cust-gen")
        public Customer createCustomer(@Signal("name") String name, @Signal("address") String address) {
            System.out.println("\n********* Creating customer " + name);
            return new Customer(name, address);
        }

        @Gear("prod-gen")
        public Product createProduct(@Signal("product-name") String name, @Signal("price") int price) {
            System.out.println("\n********* Creating product " + name);
            return new Product(name, price);
        }
    }

    @Test
    public void testTrigger() {
        System.out.println("Triggering Customer creation only");
        Signals input = new Signals();
        input.add("name", "John Doe");
        input.add("address", "123 Main St");

        GearTrigger.Result result = GearTrigger.run(input);
        Assertions.assertNotNull(result);
        System.out.println("Outputs: ");
        for (String signalName : result.outputs().signalNames()) {
            System.out.println(signalName);
        }
        Assertions.assertEquals(1, result.outputs().numSignals());
        Assertions.assertTrue(result.outputs().hasSignal(Customer.class.getName()));

        System.out.println("Triggering Product creation only");
        input = new Signals();
        input.add("product-name", "Widget");
        input.add("price", 100);
        result = GearTrigger.run(input);
        Assertions.assertNotNull(result);
        System.out.println("Outputs: ");
        for (String signalName : result.outputs().signalNames()) {
            System.out.println(signalName);
        }
        Assertions.assertEquals(1, result.outputs().numSignals());
        Assertions.assertTrue(result.outputs().hasSignal(Product.class.getName()));

        System.out.println("Triggering Order creation");
        input = new Signals();
        input.add("name", "John Doe");
        input.add("address", "123 Main St");
        input.add("product-name", "Widget");
        input.add("price", 100);

        result = GearTrigger.run(input);
        Assertions.assertNotNull(result);
        System.out.println("Outputs: ");
        for (String signalName : result.outputs().signalNames()) {
            System.out.println(signalName);
        }
        Map<String, List<Object>> outputs = result.outputs().signals();
        result.consumedSignals().forEach((key) -> outputs.remove(key));
        Assertions.assertEquals(1, outputs.size());
        Assertions.assertTrue(outputs.containsKey(Order.class.getName()));
    }
}
