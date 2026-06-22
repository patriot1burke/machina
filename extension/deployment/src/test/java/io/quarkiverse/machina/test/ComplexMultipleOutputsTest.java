package io.quarkiverse.machina.test;

import java.util.List;
import java.util.Map;
import java.util.Objects;
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

public class ComplexMultipleOutputsTest {
    static Logger log = Logger.getLogger(ComplexMultipleOutputsTest.class);
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(Customer.class, Product.class, Order.class,
                            GearBunch.class, OrderProcessor.class));

    public record Customer(String name, String address) {
        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass())
                return false;
            Customer customer = (Customer) o;
            return Objects.equals(name, customer.name);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name);
        }
    }

    public record Product(String name, int price) {
        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass())
                return false;
            Product product = (Product) o;
            return Objects.equals(name, product.name);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name);
        }
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

        @Gear("inventory")
        public void inventory(SignalProducer<Product> products) {
            System.out.println("\n********* Inventoried");
            products.produce(new Product("iPhone", 1000));
            products.produce(new Product("iPad", 1200));
        }

        @Gear("price-picker")
        @Signal("discount")
        public double calculateDiscount() {
            System.out.println("\n****** DISCOUNT");
            return 0.1;
        }

        @Gear("sale")
        public void sale(@Signal("discount") double discount, SignalProducer<Product> products) {
            System.out.println("\n****** SALE");
            products.produce(new Product("iWatch", (int) (500 * (1 - discount))));
        }

        @Gear("order-gen")
        public Order createOrder(List<Product> products, Customer customer) {
            System.out.println("\n********* Creating order");
            return new Order(products, customer);
        }
    }

    @Machine
    public static interface OrderProcessor {

        Order buySaleItems(@Signal("name") String name, @Signal("address") String address);

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
        Assertions.assertEquals(3, order.product().size());
        Map<String, Product> productMap = order.product().stream().collect(Collectors.toMap(Product::name, p -> p));

        Assertions.assertTrue(productMap.containsKey("iPhone"));
        Assertions.assertEquals("iPad", productMap.get("iPad").name());
        Assertions.assertEquals("iWatch", productMap.get("iWatch").name());
        Assertions.assertEquals("John", order.customer().name());
        Assertions.assertEquals("123 Main St", order.customer().address());
        System.out.println("============ end test()");
    }

}
