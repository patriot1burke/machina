package io.quarkiverse.machina.test;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.machina.Gear;
import io.quarkiverse.machina.Machine;
import io.quarkiverse.machina.Signal;
import io.quarkiverse.machina.SignalProducer;
import io.quarkus.test.QuarkusUnitTest;

public class BasicGearTest {
    static Logger log = Logger.getLogger(BasicGearTest.class);
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(Customer.class, Product.class, Order.class,
                            GearBunch.class, OrderProcessor.class));

    public record Customer(String name, String address) {
    }

    public record Product(String name, int price) {
    }

    public record Order(List<Product> product, Customer customer) {
    }

    @ApplicationScoped
    public static class GearBunch {

        @Gear("cust-gen")
        public Customer createCustomer(@Signal("name") String name, @Signal("address") String address) {
            System.out.println("\n********* Creating customer " + name);
            return new Customer(name, address);
        }

        @Gear("on-sale")
        public void onSale(SignalProducer<Product> products) {
            System.out.println("\n********* On sale");
            products.produce(new Product("iPhone", 1000));
            products.produce(new Product("iPad", 1200));
        }

        @Gear("order-gen")
        public Order createOrder(List<Product> products, Customer customer) {
            System.out.println("\n********* Creating order");
            return new Order(products, customer);
        }

    }

    @Machine
    public static interface OrderProcessor {

        public Order buySaleItems(@Signal("name") String name, @Signal("address") String address);

    }

    @Inject
    OrderProcessor processor;

    @Test
    @ActivateRequestContext
    public void test() {
        System.out.println(">>>>>>>>>>>>>>>>>>> running test()");
        Order order = processor.buySaleItems("John", "123 Main St");
        System.out.println(order);
        System.out.println("============ end test()");
    }
}
