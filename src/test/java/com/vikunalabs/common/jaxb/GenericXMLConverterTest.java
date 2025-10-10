package com.vikunalabs.common.jaxb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GenericXMLConverterTest {

    // Test classes with @XmlRootElement - using field access
    @XmlRootElement(name = "user")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class User {
        @XmlElement
        private String name;

        @XmlElement
        private int age;

        // Required no-arg constructor for JAXB
        public User() {}

        public User(String name, int age) {
            this.name = name;
            this.age = age;
        }

        // Getters and setters - no annotations here
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            User user = (User) o;
            return age == user.age && java.util.Objects.equals(name, user.name);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(name, age);
        }
    }

    // Test class without @XmlRootElement - using property access
    @XmlType(propOrder = {"id", "title", "price"})
    @XmlAccessorType(XmlAccessType.PROPERTY)
    public static class Product {
        private String id;
        private String title;
        private double price;

        public Product() {}

        public Product(String id, String title, double price) {
            this.id = id;
            this.title = title;
            this.price = price;
        }

        @XmlElement
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        @XmlElement
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        @XmlElement
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Product product = (Product) o;
            return Double.compare(product.price, price) == 0 &&
                    java.util.Objects.equals(id, product.id) &&
                    java.util.Objects.equals(title, product.title);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(id, title, price);
        }
    }

    // Additional test class for complex scenarios - using field access
    @XmlRootElement(name = "order")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Order {
        @XmlElement
        private String orderId;

        @XmlElement
        private String customerName;

        @XmlElement
        private double totalAmount;

        public Order() {}

        public Order(String orderId, String customerName, double totalAmount) {
            this.orderId = orderId;
            this.customerName = customerName;
            this.totalAmount = totalAmount;
        }

        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }

        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }

        public double getTotalAmount() { return totalAmount; }
        public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Order order = (Order) o;
            return Double.compare(order.totalAmount, totalAmount) == 0 &&
                    java.util.Objects.equals(orderId, order.orderId) &&
                    java.util.Objects.equals(customerName, order.customerName);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(orderId, customerName, totalAmount);
        }
    }

    public static abstract class AbstractClass {
        private String data;

        public String getData() { return data; }
        public void setData(String data) { this.data = data; }
    }

    // Add this test class specifically for namespace testing
    @XmlRootElement(name = "user", namespace = "http://example.com")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class NamespacedUser {
        @XmlElement(namespace = "http://example.com")
        private String name;

        @XmlElement(namespace = "http://example.com")
        private int age;

        public NamespacedUser() {}

        public NamespacedUser(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NamespacedUser user = (NamespacedUser) o;
            return age == user.age && java.util.Objects.equals(name, user.name);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(name, age);
        }
    }

    @BeforeEach
    void setUp() {
        // Clear caches before each test to ensure isolation
        GenericXMLConverter.clearCache();
        GenericXMLConverter.clearThreadLocalCache();
    }

    @AfterEach
    void tearDown() {
        // Clean up after each test
        GenericXMLConverter.clearThreadLocalCache();
    }

    @Test
    @DisplayName("Should convert XML to object when class has XmlRootElement")
    void convertXmlToObject_withXmlRootElement() throws JAXBException {
        // Given
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<user><name>John Doe</name><age>30</age></user>";
        User expected = new User("John Doe", 30);

        // When
        User result = GenericXMLConverter.convertXmlToObject(xml, User.class);

        // Then
        assertNotNull(result);
        assertEquals(expected, result);
        assertEquals("John Doe", result.getName());
        assertEquals(30, result.getAge());
    }

    @Test
    @DisplayName("Should convert XML to object when class lacks XmlRootElement")
    void convertXmlToObject_withoutXmlRootElement() throws JAXBException {
        // Given
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<product><id>123</id><title>Laptop</title><price>999.99</price></product>";
        Product expected = new Product("123", "Laptop", 999.99);

        // When
        Product result = GenericXMLConverter.convertXmlToObject(xml, Product.class);

        // Then
        assertNotNull(result);
        assertEquals(expected, result);
        assertEquals("123", result.getId());
        assertEquals("Laptop", result.getTitle());
        assertEquals(999.99, result.getPrice(), 0.001);
    }

    @Test
    @DisplayName("Should return null for null XML input")
    void convertXmlToObject_withNullInput() throws JAXBException {
        // When
        User result = GenericXMLConverter.convertXmlToObject(null, User.class);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Should return null for empty XML input")
    void convertXmlToObject_withEmptyInput() throws JAXBException {
        // When
        User result = GenericXMLConverter.convertXmlToObject("", User.class);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Should return null for whitespace-only XML input")
    void convertXmlToObject_withWhitespaceInput() throws JAXBException {
        // When
        User result = GenericXMLConverter.convertXmlToObject("   ", User.class);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Should return null for blank XML input with tabs and newlines")
    void convertXmlToObject_withBlankInput() throws JAXBException {
        // When
        User result = GenericXMLConverter.convertXmlToObject("\t\n  \r\n", User.class);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Should handle XML with namespaces")
    void convertXmlToObject_withNamespaces() throws JAXBException {
        // Given
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<ns:user xmlns:ns=\"http://example.com\">" +
                "<ns:name>Jane Smith</ns:name><ns:age>25</ns:age></ns:user>";

        // When
        NamespacedUser result = GenericXMLConverter.convertXmlToObject(xml, NamespacedUser.class);

        // Then
        assertNotNull(result);
        assertEquals("Jane Smith", result.getName());
        assertEquals(25, result.getAge());
    }

    @Test
    @DisplayName("Should handle malformed XML gracefully")
    void convertXmlToObject_withMalformedXml() {
        // Given
        String malformedXml = "<user><name>John</name><age>30</user>"; // Missing closing age tag

        // When & Then
        assertThrows(JAXBException.class, () -> {
            GenericXMLConverter.convertXmlToObject(malformedXml, User.class);
        });
    }

    @Test
    @DisplayName("Should handle invalid target class gracefully")
    void convertXmlToObject_withInvalidClass() {
        // Given
        // Complex XML structure with simple target type that cannot handle it
        String xml = "<user><name>John</name><age>30</age></user>";

        // When & Then
        // Integer is a simple type that cannot unmarshal complex XML structure
        assertThrows(JAXBException.class, () -> {
            GenericXMLConverter.convertXmlToObject(xml, AbstractClass.class);
        });
    }

    @Test
    @DisplayName("Should cache JAXBContext for repeated use")
    void convertXmlToObject_shouldCacheJAXBContext() throws JAXBException {
        // Given
        String xml1 = "<user><name>User1</name><age>20</age></user>";
        String xml2 = "<user><name>User2</name><age>25</age></user>";

        // When - convert multiple times with same class
        User user1 = GenericXMLConverter.convertXmlToObject(xml1, User.class);
        User user2 = GenericXMLConverter.convertXmlToObject(xml2, User.class);

        // Then - both should work without exceptions
        assertNotNull(user1);
        assertNotNull(user2);
        assertEquals("User1", user1.getName());
        assertEquals("User2", user2.getName());
    }

    @Test
    @DisplayName("Should reuse Unmarshaller for better performance")
    void convertXmlToObject_shouldReuseUnmarshaller() throws JAXBException {
        // Given
        String xml1 = "<user><name>User1</name><age>20</age></user>";
        String xml2 = "<user><name>User2</name><age>25</age></user>";
        String xml3 = "<user><name>User3</name><age>30</age></user>";

        // When - convert multiple times (unmarshallers should be reused)
        User user1 = GenericXMLConverter.convertXmlToObject(xml1, User.class);
        User user2 = GenericXMLConverter.convertXmlToObject(xml2, User.class);
        User user3 = GenericXMLConverter.convertXmlToObject(xml3, User.class);

        // Then - all should work correctly
        assertNotNull(user1);
        assertNotNull(user2);
        assertNotNull(user3);
        assertEquals("User1", user1.getName());
        assertEquals("User2", user2.getName());
        assertEquals("User3", user3.getName());
    }

    @Test
    @DisplayName("Should handle XML with special characters")
    void convertXmlToObject_withSpecialCharacters() throws JAXBException {
        // Given
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<user><name>John &amp; Jane</name><age>30</age></user>";

        // When
        User result = GenericXMLConverter.convertXmlToObject(xml, User.class);

        // Then
        assertNotNull(result);
        assertEquals("John & Jane", result.getName());
    }

    @Test
    @DisplayName("Should handle XML with CDATA sections")
    void convertXmlToObject_withCDATA() throws JAXBException {
        // Given
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<user><name><![CDATA[John <Doe>]]></name><age>30</age></user>";

        // When
        User result = GenericXMLConverter.convertXmlToObject(xml, User.class);

        // Then
        assertNotNull(result);
        assertEquals("John <Doe>", result.getName());
    }

    @Test
    @DisplayName("Should handle concurrent access safely with same class")
    void convertXmlToObject_concurrentAccessSameClass() throws InterruptedException {
        // Given
        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);

        String xml = "<user><name>User</name><age>30</age></user>";

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // When
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    User result = GenericXMLConverter.convertXmlToObject(xml, User.class);
                    assertNotNull(result);
                    assertEquals("User", result.getName());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads at once
        endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Then
        assertTrue(exceptions.isEmpty(), "No exceptions should occur: " + exceptions);
        assertEquals(threadCount, successCount.get(), "All threads should succeed");
    }

    @Test
    @DisplayName("Should handle concurrent access with different classes")
    void convertXmlToObject_concurrentAccessDifferentClasses() throws InterruptedException {
        // Given
        int threadCount = 30;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);

        String userXml = "<user><name>User</name><age>30</age></user>";
        String productXml = "<product><id>P123</id><title>Product</title><price>99.99</price></product>";
        String orderXml = "<order><orderId>O456</orderId><customerName>Customer</customerName><totalAmount>199.99</totalAmount></order>";

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // When
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    // Rotate between different classes
                    if (index % 3 == 0) {
                        User result = GenericXMLConverter.convertXmlToObject(userXml, User.class);
                        assertNotNull(result);
                    } else if (index % 3 == 1) {
                        Product result = GenericXMLConverter.convertXmlToObject(productXml, Product.class);
                        assertNotNull(result);
                    } else {
                        Order result = GenericXMLConverter.convertXmlToObject(orderXml, Order.class);
                        assertNotNull(result);
                    }

                    successCount.incrementAndGet();
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Then
        assertTrue(exceptions.isEmpty(), "No exceptions should occur: " + exceptions);
        assertEquals(threadCount, successCount.get(), "All threads should succeed");
    }

    @Test
    @DisplayName("Should handle XML with different encodings")
    void convertXmlToObject_withEncodingDeclaration() throws JAXBException {
        // Given
        String xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" +
                "<user><name>José</name><age>30</age></user>";

        // When
        User result = GenericXMLConverter.convertXmlToObject(xml, User.class);

        // Then
        assertNotNull(result);
        assertEquals("José", result.getName());
    }

    @Test
    @DisplayName("Should handle XML without declaration")
    void convertXmlToObject_withoutXmlDeclaration() throws JAXBException {
        // Given
        String xml = "<user><name>Alice</name><age>28</age></user>";

        // When
        User result = GenericXMLConverter.convertXmlToObject(xml, User.class);

        // Then
        assertNotNull(result);
        assertEquals("Alice", result.getName());
        assertEquals(28, result.getAge());
    }

    @Test
    @DisplayName("Should handle XML with comments")
    void convertXmlToObject_withComments() throws JAXBException {
        // Given
        String xml = "<?xml version=\"1.0\"?>" +
                "<!-- This is a comment -->" +
                "<user><name>Bob</name><!-- Another comment --><age>35</age></user>";

        // When
        User result = GenericXMLConverter.convertXmlToObject(xml, User.class);

        // Then
        assertNotNull(result);
        assertEquals("Bob", result.getName());
        assertEquals(35, result.getAge());
    }

    @Test
    @DisplayName("Should handle Product without XmlRootElement repeatedly")
    void convertXmlToObject_productRepeatedConversions() throws JAXBException {
        // Given
        String xml1 = "<product><id>P1</id><title>Product 1</title><price>10.00</price></product>";
        String xml2 = "<product><id>P2</id><title>Product 2</title><price>20.00</price></product>";
        String xml3 = "<product><id>P3</id><title>Product 3</title><price>30.00</price></product>";

        // When
        Product p1 = GenericXMLConverter.convertXmlToObject(xml1, Product.class);
        Product p2 = GenericXMLConverter.convertXmlToObject(xml2, Product.class);
        Product p3 = GenericXMLConverter.convertXmlToObject(xml3, Product.class);

        // Then
        assertNotNull(p1);
        assertNotNull(p2);
        assertNotNull(p3);
        assertEquals("P1", p1.getId());
        assertEquals("P2", p2.getId());
        assertEquals("P3", p3.getId());
    }

    @Test
    @DisplayName("Should clear cache successfully")
    void clearCache_shouldWork() throws JAXBException {
        // Given
        String xml = "<user><name>Test</name><age>25</age></user>";
        GenericXMLConverter.convertXmlToObject(xml, User.class);

        // When
        GenericXMLConverter.clearCache();
        User result = GenericXMLConverter.convertXmlToObject(xml, User.class);

        // Then - should still work after cache clear
        assertNotNull(result);
        assertEquals("Test", result.getName());
    }

    @Test
    @DisplayName("Should clear thread local cache successfully")
    void clearThreadLocalCache_shouldWork() throws JAXBException {
        // Given
        String xml = "<user><name>Test</name><age>25</age></user>";
        GenericXMLConverter.convertXmlToObject(xml, User.class);

        // When
        GenericXMLConverter.clearThreadLocalCache();
        User result = GenericXMLConverter.convertXmlToObject(xml, User.class);

        // Then - should still work after cache clear
        assertNotNull(result);
        assertEquals("Test", result.getName());
    }

    @Test
    @DisplayName("Should handle empty element values")
    void convertXmlToObject_withEmptyElements() throws JAXBException {
        // Given
        String xml = "<user><name></name><age>0</age></user>";

        // When
        User result = GenericXMLConverter.convertXmlToObject(xml, User.class);

        // Then
        assertNotNull(result);
        assertEquals("", result.getName());
        assertEquals(0, result.getAge());
    }

    @Test
    @DisplayName("Should handle XML with extra whitespace")
    void convertXmlToObject_withExtraWhitespace() throws JAXBException {
        // Given
        String xml = "  \n\t  <?xml version=\"1.0\"?>\n" +
                "  <user>  \n" +
                "    <name>  Whitespace Test  </name>\n" +
                "    <age>40</age>\n" +
                "  </user>  \n  ";

        // When
        User result = GenericXMLConverter.convertXmlToObject(xml, User.class);

        // Then
        assertNotNull(result);
        assertTrue(result.getName().contains("Whitespace Test"));
        assertEquals(40, result.getAge());
    }

    @Test
    @DisplayName("Should throw exception for completely invalid XML")
    void convertXmlToObject_withCompletelyInvalidXml() {
        // Given
        String invalidXml = "This is not XML at all!";

        // When & Then
        assertThrows(JAXBException.class, () -> {
            GenericXMLConverter.convertXmlToObject(invalidXml, User.class);
        });
    }

    @Test
    @DisplayName("Should handle XML with mismatched tags")
    void convertXmlToObject_withMismatchedTags() {
        // Given
        String xml = "<user><name>Test</name><age>25</user>"; // Missing </age>

        // When & Then
        assertThrows(JAXBException.class, () -> {
            GenericXMLConverter.convertXmlToObject(xml, User.class);
        });
    }
}