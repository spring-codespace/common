package com.vikunalabs.common.jaxb;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlRootElement;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced XML converter with improved efficiency through:
 * - JAXBContext caching (thread-safe)
 * - Unmarshaller pooling per thread
 * - Annotation-based routing to avoid exception overhead
 * - Security hardening against XXE attacks
 */
public class GenericXMLConverter {

    private static final Map<Class<?>, JAXBContext> CONTEXT_CACHE = new ConcurrentHashMap<>();

    // Thread-local cache for Unmarshallers to avoid creation overhead
    private static final ThreadLocal<Map<Class<?>, Unmarshaller>> UNMARSHALLER_CACHE =
            ThreadLocal.withInitial(HashMap::new);

    /**
     * Converts XML string to object, handling cases where JAXB classes might not have @XmlRootElement.
     *
     * @param xmlString the XML string to convert
     * @param targetClass the target class type
     * @return the deserialized object, or null if input is null/empty
     * @throws JAXBException if unmarshalling fails
     */
    public static <T> T convertXmlToObject(String xmlString, Class<T> targetClass) throws JAXBException {
        if (xmlString == null || xmlString.isEmpty() || xmlString.isBlank()) {
            return null;
        }

        // Trim leading/trailing whitespace to handle cases where XML declaration is not at the very beginning
        xmlString = xmlString.trim();

        try {
            JAXBContext context = getOrCreateContext(targetClass);
            Unmarshaller unmarshaller = getOrCreateUnmarshaller(context, targetClass);

            // Check for @XmlRootElement to determine unmarshalling strategy
            // This avoids expensive exception-based control flow
            if (hasXmlRootElement(targetClass)) {
                return unmarshalDirect(xmlString, targetClass, unmarshaller);
            } else {
                return unmarshalWithStreamReader(xmlString, targetClass, unmarshaller);
            }

        } catch (RuntimeException e) {
            // Unwrap RuntimeExceptions that wrap JAXBExceptions
            Throwable cause = e.getCause();
            if (cause instanceof JAXBException) {
                throw (JAXBException) cause;
            }
            throw new JAXBException("Failed to convert XML to " + targetClass.getSimpleName(), e);
        }
    }

    /**
     * Gets or creates a cached JAXBContext for the given class.
     * JAXBContext creation is expensive, so we cache it per class.
     */
    private static <T> JAXBContext getOrCreateContext(Class<T> targetClass) throws JAXBException {
        return CONTEXT_CACHE.computeIfAbsent(targetClass, clazz -> {
            try {
                return JAXBContext.newInstance(clazz);
            } catch (JAXBException e) {
                throw new RuntimeException("Failed to create JAXBContext for " + clazz.getName(), e);
            }
        });
    }

    /**
     * Gets or creates a cached Unmarshaller for the given class.
     * Uses ThreadLocal to ensure thread-safety without synchronization overhead.
     */
    private static <T> Unmarshaller getOrCreateUnmarshaller(JAXBContext context, Class<T> targetClass)
            throws JAXBException {
        Map<Class<?>, Unmarshaller> cache = UNMARSHALLER_CACHE.get();

        return cache.computeIfAbsent(targetClass, clazz -> {
            try {
                return context.createUnmarshaller();
            } catch (JAXBException e) {
                throw new RuntimeException("Failed to create Unmarshaller for " + clazz.getName(), e);
            }
        });
    }

    /**
     * Checks if a class has the @XmlRootElement annotation.
     */
    private static boolean hasXmlRootElement(Class<?> clazz) {
        return clazz.isAnnotationPresent(XmlRootElement.class);
    }

    /**
     * Direct unmarshalling for classes with @XmlRootElement annotation.
     */
    private static <T> T unmarshalDirect(String xmlString, Class<T> targetClass,
                                         Unmarshaller unmarshaller) throws JAXBException {
        try (StringReader reader = new StringReader(xmlString)) {
            return targetClass.cast(unmarshaller.unmarshal(reader));
        }
    }

    /**
     * Unmarshalling using XMLStreamReader for classes without @XmlRootElement.
     * Creates a secure XMLInputFactory per invocation to avoid thread-safety issues.
     */
    private static <T> T unmarshalWithStreamReader(String xmlString, Class<T> targetClass,
                                                   Unmarshaller unmarshaller) throws JAXBException {
        XMLInputFactory factory = createSecureXmlInputFactory();

        try (StringReader reader = new StringReader(xmlString)) {
            XMLStreamReader xmlStreamReader = factory.createXMLStreamReader(reader);

            try {
                // Skip to the start element
                while (xmlStreamReader.hasNext() && !xmlStreamReader.isStartElement()) {
                    xmlStreamReader.next();
                }

                if (!xmlStreamReader.isStartElement()) {
                    throw new JAXBException("No start element found in XML");
                }

                return unmarshaller.unmarshal(xmlStreamReader, targetClass).getValue();

            } finally {
                // Ensure XMLStreamReader is closed to free resources
                try {
                    xmlStreamReader.close();
                } catch (XMLStreamException closeEx) {
                    // Swallow close exception - already unmarshalled successfully
                    // In production, log this warning
                }
            }
        } catch (XMLStreamException e) {
            throw new JAXBException("Failed to parse XML stream", e);
        }
    }

    /**
     * Creates a secure XMLInputFactory with XXE protection enabled.
     */
    private static XMLInputFactory createSecureXmlInputFactory() {
        XMLInputFactory factory = XMLInputFactory.newInstance();

        // Security hardening: disable dangerous XML features to prevent XXE attacks
        try {
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        } catch (IllegalArgumentException e) {
            // Some implementations might not support these properties
            // In production, log a warning here
        }

        return factory;
    }

    /**
     * Clears all caches. Useful for testing or when you need to free memory.
     * Note: This only clears the JAXBContext cache, not ThreadLocal caches.
     */
    public static void clearCache() {
        CONTEXT_CACHE.clear();
        // ThreadLocal caches will be cleared when threads die
    }

    /**
     * Removes thread-local cache for the current thread.
     * Call this when a thread is done using the converter to free memory.
     */
    public static void clearThreadLocalCache() {
        UNMARSHALLER_CACHE.remove();
    }
}