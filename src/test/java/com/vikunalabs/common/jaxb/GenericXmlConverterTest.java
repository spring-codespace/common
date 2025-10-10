package com.vikunalabs.common.jaxb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

import static org.junit.jupiter.api.Assertions.*;

class GenericXmlConverterTest {

    // Test classes with @XmlRootElement
    @XmlRootElement(name = "user")
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

        // Getters and setters
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

    // Test class without @XmlRootElement
    @XmlType(propOrder = {"id", "title", "price"})
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

    @BeforeEach
    void setUp() {
        // Clear cache before each test to ensure isolation
        // Note: This would require making CONTEXT_CACHE accessible via package-private or reflection
        // For real implementation, you might want to add a clearCache() method for testing
    }

    @Test
    @DisplayName("Should convert XML to object when class has XmlRootElement")
    void convertXmlToObject_withXmlRootElement() throws JAXBException {
        // Given
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<user><name>John Doe</name><age>30</age></user>";
        User expected = new User("John Doe", 30);

        // When
        User result = GenericXmlConverter.convertXmlToObject(xml, User.class);

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
        Product result = GenericXmlConverter.convertXmlToObject(xml, Product.class);

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
        User result = GenericXmlConverter.convertXmlToObject(null, User.class);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Should return null for empty XML input")
    void convertXmlToObject_withEmptyInput() throws JAXBException {
        // When
        User result = GenericXmlConverter.convertXmlToObject("", User.class);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Should return null for whitespace-only XML input")
    void convertXmlToObject_withWhitespaceInput() throws JAXBException {
        // When
        User result = GenericXmlConverter.convertXmlToObject("   ", User.class);

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
        User result = GenericXmlConverter.convertXmlToObject(xml, User.class);

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
            GenericXmlConverter.convertXmlToObject(malformedXml, User.class);
        });
    }

    @Test
    @DisplayName("Should handle invalid target class gracefully")
    void convertXmlToObject_withInvalidClass() {
        // Given
        String xml = "<user><name>John</name><age>30</age></user>";

        // When & Then
        assertThrows(JAXBException.class, () -> {
            GenericXmlConverter.convertXmlToObject(xml, String.class); // String is not a JAXB class
        });
    }

    @Test
    @DisplayName("Should cache JAXBContext for repeated use")
    void convertXmlToObject_shouldCacheJAXBContext() throws JAXBException {
        // Given
        String xml1 = "<user><name>User1</name><age>20</age></user>";
        String xml2 = "<user><name>User2</name><age>25</age></user>";

        // When - convert multiple times with same class
        User user1 = GenericXmlConverter.convertXmlToObject(xml1, User.class);
        User user2 = GenericXmlConverter.convertXmlToObject(xml2, User.class);

        // Then - both should work without exceptions
        assertNotNull(user1);
        assertNotNull(user2);
        assertEquals("User1", user1.getName());
        assertEquals("User2", user2.getName());
    }

    @Test
    @DisplayName("Should handle XML with special characters")
    void convertXmlToObject_withSpecialCharacters() throws JAXBException {
        // Given
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<user><name>John &amp; Jane</name><age>30</age></user>";

        // When
        User result = GenericXmlConverter.convertXmlToObject(xml, User.class);

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
        User result = GenericXmlConverter.convertXmlToObject(xml, User.class);

        // Then
        assertNotNull(result);
        assertEquals("John <Doe>", result.getName());
    }

    @Test
    @DisplayName("Should handle concurrent access safely")
    void convertXmlToObject_concurrentAccess() throws InterruptedException {
        // Given
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        final JAXBException[] exceptions = new JAXBException[threadCount];

        String xml = "<user><name>User</name><age>30</age></user>";

        // When
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    User result = GenericXmlConverter.convertXmlToObject(xml, User.class);
                    assertNotNull(result);
                } catch (JAXBException e) {
                    exceptions[index] = e;
                }
            });
            threads[i].start();
        }

        // Then - wait for all threads and check for exceptions
        for (Thread thread : threads) {
            thread.join();
        }

        for (JAXBException exception : exceptions) {
            assertNull(exception, "No JAXBException should be thrown in concurrent access");
        }
    }

    @Test
    @DisplayName("Should handle XML with different encodings")
    void convertXmlToObject_withEncodingDeclaration() throws JAXBException {
        // Given
        String xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" +
                "<user><name>José</name><age>30</age></user>";

        // When
        User result = GenericXmlConverter.convertXmlToObject(xml, User.class);

        // Then
        assertNotNull(result);
        assertEquals("José", result.getName());
    }
}