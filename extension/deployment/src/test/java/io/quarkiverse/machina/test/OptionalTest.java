package io.quarkiverse.machina.test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.machina.Gear;
import io.quarkiverse.machina.Machine;
import io.quarkiverse.machina.Signal;
import io.quarkiverse.machina.SignalProducer;
import io.quarkus.test.QuarkusUnitTest;

public class OptionalTest {
    static Logger log = Logger.getLogger(OptionalTest.class);
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

    public record Gift(String name) {

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

        @Gear("gift")
        public Product noGift(Optional<Gift> gift) {
            System.out.println("\n********* noGift");
            return new Product("No Gift", 0);
        }

        @Gear("gift from address")
        public Product giftFromAddress(@Signal("address") Optional<String> gift) {
            System.out.println("\n********* giftFromAddress");
            return new Product(gift.get(), 0);
        }

        @Gear("from-customer")
        public Product fromCustomer(Optional<List<Customer>> customers) {
            System.out.println("\n********* from-customer");
            Assertions.assertTrue(customers.isPresent());
            Assertions.assertEquals(1, customers.get().size());
            return new Product("from-customer", 0);
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
        Assertions.assertNotNull(order);
        Assertions.assertEquals(5, order.product().size());
        Map<String, Product> productMap = order.product().stream().collect(Collectors.toMap(Product::name, p -> p));

        Assertions.assertTrue(productMap.containsKey("iPhone"));
        Assertions.assertTrue(productMap.containsKey("iPad"));
        Assertions.assertTrue(productMap.containsKey("from-customer"));
        Assertions.assertTrue(productMap.containsKey("No Gift"));
        Assertions.assertTrue(productMap.containsKey("123 Main St"));
        Assertions.assertEquals("John", order.customer().name());
        Assertions.assertEquals("123 Main St", order.customer().address());
        System.out.println("============ end test()");
    }
}
