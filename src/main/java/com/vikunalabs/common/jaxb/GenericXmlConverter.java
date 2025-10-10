package com.vikunalabs.common.jaxb;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GenericXmlConverter {
    
    private static final Map<Class<?>, JAXBContext> CONTEXT_CACHE = new ConcurrentHashMap<>();
    private static final XMLInputFactory XML_INPUT_FACTORY = createSecureXmlInputFactory();
    
    private static XMLInputFactory createSecureXmlInputFactory() {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        // Security hardening: disable dangerous XML features
        try {
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        } catch (IllegalArgumentException e) {
            // Some implementations might not support these properties
            // Log a warning in real application
        }
        return factory;
    }
    
    /**
     * Converts XML string to object, handling cases where JAXB classes might not have @XmlRootElement
     */
    public static <T> T convertXmlToObject(String xmlString, Class<T> targetClass) throws JAXBException {
        if (xmlString == null || xmlString.trim().isEmpty()) {
            return null;
        }
        
        try {
            JAXBContext context = getOrCreateContext(targetClass);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            
            return unmarshalXml(xmlString.trim(), targetClass, unmarshaller);
            
        } catch (RuntimeException e) {
            // Unwrap RuntimeExceptions that wrap JAXBExceptions
            Throwable cause = e.getCause();
            if (cause instanceof JAXBException) {
                throw (JAXBException) cause;
            }
            throw new JAXBException("Failed to convert XML to " + targetClass.getSimpleName(), e);
        }
    }
    
    private static <T> JAXBContext getOrCreateContext(Class<T> targetClass) throws JAXBException {
        return CONTEXT_CACHE.computeIfAbsent(targetClass, clazz -> {
            try {
                return JAXBContext.newInstance(clazz);
            } catch (JAXBException e) {
                throw new RuntimeException("Failed to create JAXBContext for " + clazz.getName(), e);
            }
        });
    }
    
    private static <T> T unmarshalXml(String xmlString, Class<T> targetClass, Unmarshaller unmarshaller) 
            throws JAXBException {
        try {
            // Try direct unmarshalling first (works with @XmlRootElement)
            return targetClass.cast(unmarshaller.unmarshal(new StringReader(xmlString)));
        } catch (JAXBException e) {
            // If that fails, try using XMLStreamReader to skip root element validation
            return unmarshalWithStreamReader(xmlString, targetClass, unmarshaller);
        }
    }
    
    private static <T> T unmarshalWithStreamReader(String xmlString, Class<T> targetClass, 
                                                  Unmarshaller unmarshaller) throws JAXBException {
        try (StringReader reader = new StringReader(xmlString)) {
            XMLStreamReader xmlStreamReader = XML_INPUT_FACTORY.createXMLStreamReader(reader);
            
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
                // Ensure XMLStreamReader is closed
                try {
                    xmlStreamReader.close();
                } catch (XMLStreamException closeEx) {
                    // Log warning in real application
                }
            }
        } catch (XMLStreamException e) {
            throw new JAXBException("Failed to parse XML stream", e);
        }
    }
}