/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.xerces.jaxp;

import java.io.IOException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.validation.Schema;

import org.apache.xerces.impl.Constants;
import org.apache.xerces.impl.validation.ValidationManager;
import org.apache.xerces.impl.xs.XMLSchemaValidator;
import org.apache.xerces.jaxp.validation.XSGrammarPoolContainer;
import org.apache.xerces.util.SAXMessageFormatter;
import org.apache.xerces.util.SecurityManager;
import org.apache.xerces.xni.XMLDocumentHandler;
import org.apache.xerces.xni.parser.XMLComponent;
import org.apache.xerces.xni.parser.XMLComponentManager;
import org.apache.xerces.xni.parser.XMLConfigurationException;
import org.apache.xerces.xni.parser.XMLDocumentSource;
import org.apache.xerces.xni.parser.XMLParserConfiguration;
import org.apache.xerces.xs.AttributePSVI;
import org.apache.xerces.xs.ElementPSVI;
import org.apache.xerces.xs.PSVIProvider;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.HandlerBase;
import org.xml.sax.InputSource;
import org.xml.sax.Parser;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * This is the implementation specific class for the
 * <code>javax.xml.parsers.SAXParser</code>.
 * 
 * @author Rajiv Mordani
 * @author Edwin Goei
 * 
 * @version $Id$
 */
@SuppressWarnings("deprecation")
public class SAXParserImpl extends javax.xml.parsers.SAXParser
    implements JAXPConstants, PSVIProvider {
    
    /** Feature identifier: namespaces. */
    private static final String NAMESPACES_FEATURE =
        Constants.SAX_FEATURE_PREFIX + Constants.NAMESPACES_FEATURE;
    
    /** Feature identifier: namespace prefixes. */
    private static final String NAMESPACE_PREFIXES_FEATURE =
        Constants.SAX_FEATURE_PREFIX + Constants.NAMESPACE_PREFIXES_FEATURE;
    
    /** Feature identifier: validation. */
    private static final String VALIDATION_FEATURE =
        Constants.SAX_FEATURE_PREFIX + Constants.VALIDATION_FEATURE;
    
    /** Feature identifier: XML Schema validation */
    private static final String XMLSCHEMA_VALIDATION_FEATURE =
        Constants.XERCES_FEATURE_PREFIX + Constants.SCHEMA_VALIDATION_FEATURE;
    
    /** Feature identifier: XInclude processing */
    private static final String XINCLUDE_FEATURE = 
        Constants.XERCES_FEATURE_PREFIX + Constants.XINCLUDE_FEATURE;
    
    /** Property identifier: security manager. */
    private static final String SECURITY_MANAGER =
        Constants.XERCES_PROPERTY_PREFIX + Constants.SECURITY_MANAGER_PROPERTY;

    private final JAXPSAXParser xmlReader;
    private String schemaLanguage = null;     // null means DTD
    private final Schema grammar;
    
    private final XMLComponent fSchemaValidator;
    private final XMLComponentManager fSchemaValidatorComponentManager;
    private final ValidationManager fSchemaValidationManager;
    private final UnparsedEntityHandler fUnparsedEntityHandler;
    
    /** Initial ErrorHandler */
    private final ErrorHandler fInitErrorHandler;
    
    /** Initial EntityResolver */
    private final EntityResolver fInitEntityResolver;
    
    /**
     * Create a SAX parser with the associated features
     * @param features Hashtable of SAX features, may be null
     */
    SAXParserImpl(SAXParserFactoryImpl spf, Hashtable<?, ?> features) 
        throws SAXException {
        this(spf, features, false);
    }
    
    /**
     * Create a SAX parser with the associated features
     * @param features Hashtable of SAX features, may be null
     */
    SAXParserImpl(SAXParserFactoryImpl spf, Hashtable<?, ?> features, boolean secureProcessing)
        throws SAXException
    {
        // Instantiate a SAXParser directly and not through SAX so that we use the right ClassLoader
        xmlReader = new JAXPSAXParser(this);

        // JAXP "namespaceAware" == SAX Namespaces feature
        // Note: there is a compatibility problem here with default values:
        // JAXP default is false while SAX 2 default is true!
        xmlReader.setFeature0(NAMESPACES_FEATURE, spf.isNamespaceAware());

        // SAX "namespaces" and "namespace-prefixes" features should not
        // both be false.  We make them opposite for backward compatibility
        // since JAXP 1.0 apps may want to receive xmlns* attributes.
        xmlReader.setFeature0(NAMESPACE_PREFIXES_FEATURE, !spf.isNamespaceAware());
        
        // Avoid setting the XInclude processing feature if the value is false.
        // This will keep the configuration from throwing an exception if it
        // does not support XInclude.
        if (spf.isXIncludeAware()) {
            xmlReader.setFeature0(XINCLUDE_FEATURE, true);
        }
        
        // If the secure processing feature is on set a security manager.
        if (secureProcessing) {
            xmlReader.setProperty0(SECURITY_MANAGER, new SecurityManager());
        }
        
        // Set application's features, followed by validation features.
        setFeatures(features);
        
        // If validating, provide a default ErrorHandler that prints
        // validation errors with a warning telling the user to set an
        // ErrorHandler.
        if (spf.isValidating()) {
            fInitErrorHandler = new DefaultValidationErrorHandler();
            xmlReader.setErrorHandler(fInitErrorHandler);
        }
        else {
            fInitErrorHandler = xmlReader.getErrorHandler();
        }
        xmlReader.setFeature0(VALIDATION_FEATURE, spf.isValidating());
        
        // Get the Schema object from the factory
        this.grammar = spf.getSchema();
        if (grammar != null) {
            XMLParserConfiguration config = xmlReader.getXMLParserConfiguration();
            XMLComponent validatorComponent = null;
            /** For Xerces grammars, use built-in schema validator. **/
            if (grammar instanceof XSGrammarPoolContainer) {
                validatorComponent = new XMLSchemaValidator();
                fSchemaValidationManager = new ValidationManager();
                fUnparsedEntityHandler = new UnparsedEntityHandler(fSchemaValidationManager);
                config.setDTDHandler(fUnparsedEntityHandler);
                fUnparsedEntityHandler.setDTDHandler(xmlReader);
                xmlReader.setDTDSource(fUnparsedEntityHandler);
                fSchemaValidatorComponentManager = new SchemaValidatorConfiguration(config, 
                        (XSGrammarPoolContainer) grammar, fSchemaValidationManager);
            }
            /** For third party grammars, use the JAXP validator component. **/
            else {
                validatorComponent = new JAXPValidatorComponent(grammar.newValidatorHandler());
                fSchemaValidationManager = null;
                fUnparsedEntityHandler = null;
                fSchemaValidatorComponentManager = config;
            }
            config.addRecognizedFeatures(validatorComponent.getRecognizedFeatures());
            config.addRecognizedProperties(validatorComponent.getRecognizedProperties());
            config.setDocumentHandler((XMLDocumentHandler) validatorComponent);
            ((XMLDocumentSource)validatorComponent).setDocumentHandler(xmlReader);
            xmlReader.setDocumentSource((XMLDocumentSource) validatorComponent);
            fSchemaValidator = validatorComponent;
        }
        else {
            fSchemaValidationManager = null;
            fUnparsedEntityHandler = null;
            fSchemaValidatorComponentManager = null;
            fSchemaValidator = null;
        }
        
        // Initial EntityResolver
        fInitEntityResolver = xmlReader.getEntityResolver();
    }

    /**
     * Set any features of our XMLReader based on any features set on the
     * SAXParserFactory.
     *
     * XXX Does not handle possible conflicts between SAX feature names and
     * JAXP specific feature names, eg. SAXParserFactory.isValidating()
     */
    private void setFeatures(Hashtable<?, ?> features)
        throws SAXNotSupportedException, SAXNotRecognizedException {
        if (features != null) {
            Iterator<?> entries = features.entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) entries.next();
                String feature = (String) entry.getKey();
                boolean value = ((Boolean) entry.getValue()).booleanValue();
                xmlReader.setFeature0(feature, value);
            }
        }
    }

    public Parser getParser() throws SAXException {
        // Xerces2 AbstractSAXParser implements SAX1 Parser
        // assert(xmlReader instanceof Parser);
        return (Parser) xmlReader;
    }

    /**
     * Returns the XMLReader that is encapsulated by the implementation of
     * this class.
     */
    public XMLReader getXMLReader() {
        return xmlReader;
    }

    public boolean isNamespaceAware() {
        try {
            return xmlReader.getFeature(NAMESPACES_FEATURE);
        } 
        catch (SAXException x) {
            throw new IllegalStateException(x.getMessage());
        }
    }

    public boolean isValidating() {
        try {
            return xmlReader.getFeature(VALIDATION_FEATURE);
        } 
        catch (SAXException x) {
            throw new IllegalStateException(x.getMessage());
        }
    }
    
    /**
     * Gets the XInclude processing mode for this parser
     * @return the state of XInclude processing mode
     */
    public boolean isXIncludeAware() {
        try {
            return xmlReader.getFeature(XINCLUDE_FEATURE);
        }
        catch (SAXException exc) {
            return false;
        }
    }

    /**
     * Sets the particular property in the underlying implementation of 
     * org.xml.sax.XMLReader.
     */
    public void setProperty(String name, Object value)
        throws SAXNotRecognizedException, SAXNotSupportedException {
        xmlReader.setProperty(name, value);
    }

    /**
     * returns the particular property requested for in the underlying 
     * implementation of org.xml.sax.XMLReader.
     */
    public Object getProperty(String name)
        throws SAXNotRecognizedException, SAXNotSupportedException {
        return xmlReader.getProperty(name);
    }
    
    public void parse(InputSource is, DefaultHandler dh)
        throws SAXException, IOException {
        if (is == null) {
            throw new IllegalArgumentException();
        }
        if (dh != null) {
            xmlReader.setContentHandler(dh);
            xmlReader.setEntityResolver(dh);
            xmlReader.setErrorHandler(dh);
            xmlReader.setDTDHandler(dh);
            xmlReader.setDocumentHandler(null);
        }
        xmlReader.parse(is);
    }
    
    public void parse(InputSource is, HandlerBase hb)
        throws SAXException, IOException {
        if (is == null) {
            throw new IllegalArgumentException();
        }
        if (hb != null) {
            xmlReader.setDocumentHandler(hb);
            xmlReader.setEntityResolver(hb);
            xmlReader.setErrorHandler(hb);
            xmlReader.setDTDHandler(hb);
            xmlReader.setContentHandler(null);
        }
        xmlReader.parse(is);
    }
     
    public Schema getSchema() {
        return grammar;
    }
    
    public void reset() {
        try {
            /** Restore initial values of features and properties. **/
            xmlReader.restoreInitState();
        } 
        catch (SAXException exc) {
            // This should never happen. We only store recognized
            // features and properties in the hash maps. For now
            // just ignore it.
        }
        /** Restore various handlers. **/
        xmlReader.setContentHandler(null);
        xmlReader.setDTDHandler(null);
        if (xmlReader.getErrorHandler() != fInitErrorHandler) {
            xmlReader.setErrorHandler(fInitErrorHandler);
        }
        if (xmlReader.getEntityResolver() != fInitEntityResolver) {
            xmlReader.setEntityResolver(fInitEntityResolver);
        }
    }
    
    /*
     * PSVIProvider methods
     */

    public ElementPSVI getElementPSVI() {
        return ((PSVIProvider)xmlReader).getElementPSVI();
    }

    public AttributePSVI getAttributePSVI(int index) {
        return ((PSVIProvider)xmlReader).getAttributePSVI(index);
    }

    public AttributePSVI getAttributePSVIByName(String uri, String localname) {
        return ((PSVIProvider)xmlReader).getAttributePSVIByName(uri, localname);
    }
    
    /**
     * Extension of SAXParser. This class tracks changes to 
     * features and properties to allow the parser to be reset to
     * its initial state.
     */
    public static class JAXPSAXParser extends org.apache.xerces.parsers.SAXParser {
        
        private final HashMap<String, Boolean> fInitFeatures = new HashMap<String, Boolean>();
        private final HashMap<String, Object> fInitProperties = new HashMap<String, Object>();
        private final SAXParserImpl fSAXParser;

        public JAXPSAXParser() {
            this(null);
        }
        
        JAXPSAXParser(SAXParserImpl saxParser) {
            super();
            fSAXParser = saxParser;
        }
        
        /**
         * Override SAXParser's setFeature method to track the initial state
         * of features. This keeps us from affecting the performance of the
         * SAXParser when it is created with XMLReaderFactory.
         */
        public synchronized void setFeature(String name, boolean value)
            throws SAXNotRecognizedException, SAXNotSupportedException {
            if (name == null) {
                // TODO: Add localized error message.
                throw new NullPointerException(); 
            }
            if (name.equals(XMLConstants.FEATURE_SECURE_PROCESSING)) {
                try {
                    setProperty(SECURITY_MANAGER, value ? new SecurityManager() : null);
                }
                catch (SAXNotRecognizedException exc) {
                    // If the property is not supported 
                    // re-throw the exception if the value is true.
                    if (value) {
                        throw exc;
                    }
                }
                catch (SAXNotSupportedException exc) {
                    // If the property is not supported 
                    // re-throw the exception if the value is true.
                    if (value) {
                        throw exc;
                    }
                }
                return;
            }
            if (!fInitFeatures.containsKey(name)) {
                boolean current = super.getFeature(name);
                fInitFeatures.put(name, current ? Boolean.TRUE : Boolean.FALSE); 
            }
            /** Forward feature to the schema validator if there is one. **/
            if (fSAXParser != null && fSAXParser.fSchemaValidator != null) {
                setSchemaValidatorFeature(name, value);
            }
            super.setFeature(name, value);               
        }
        
        public synchronized boolean getFeature(String name)
            throws SAXNotRecognizedException, SAXNotSupportedException {
            if (name == null) {
                // TODO: Add localized error message.
                throw new NullPointerException(); 
            }
            if (name.equals(XMLConstants.FEATURE_SECURE_PROCESSING)) {
                try {
                    return (super.getProperty(SECURITY_MANAGER) != null);
                }
                // If the property is not supported the value must be false.
                catch (SAXException exc) {
                    return false;
                }
            }
            return super.getFeature(name);
        }
        
        /**
         * Override SAXParser's setProperty method to track the initial state
         * of properties. This keeps us from affecting the performance of the
         * SAXParser when it is created with XMLReaderFactory.
         */
        public synchronized void setProperty(String name, Object value)
            throws SAXNotRecognizedException, SAXNotSupportedException {
            if (name == null) {
                // TODO: Add localized error message.
                throw new NullPointerException(); 
            }
            if (fSAXParser != null) {
                // JAXP 1.2 support
                if (JAXP_SCHEMA_LANGUAGE.equals(name)) {
                    // The spec says if a schema is given via SAXParserFactory
                    // the JAXP 1.2 properties shouldn't be allowed.
                    if (fSAXParser.grammar != null) {
                        throw new SAXNotSupportedException(
                                SAXMessageFormatter.formatMessage(fConfiguration.getLocale(), "schema-already-specified", new Object[] {name}));
                    }
                    if ( W3C_XML_SCHEMA.equals(value) ) {
                        //None of the properties will take effect till the setValidating(true) has been called                                                        
                        if( fSAXParser.isValidating() ) {
                            fSAXParser.schemaLanguage = W3C_XML_SCHEMA;
                            setFeature(XMLSCHEMA_VALIDATION_FEATURE, true);
                            // this will allow the parser not to emit DTD-related
                            // errors, as the spec demands
                            if (!fInitProperties.containsKey(JAXP_SCHEMA_LANGUAGE)) {
                                fInitProperties.put(JAXP_SCHEMA_LANGUAGE, super.getProperty(JAXP_SCHEMA_LANGUAGE));
                            }
                            super.setProperty(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA);
                        }
                        
                    } 
                    else if (value == null) {
                        fSAXParser.schemaLanguage = null;
                        setFeature(XMLSCHEMA_VALIDATION_FEATURE, false);
                    } 
                    else {
                        // REVISIT: It would be nice if we could format this message
                        // using a user specified locale as we do in the underlying
                        // XMLReader -- mrglavas
                        throw new SAXNotSupportedException(
                            SAXMessageFormatter.formatMessage(fConfiguration.getLocale(), "schema-not-supported", null));
                    }
                    return;
                } 
                else if (JAXP_SCHEMA_SOURCE.equals(name)) {
                    // The spec says if a schema is given via SAXParserFactory
                    // the JAXP 1.2 properties shouldn't be allowed.
                    if (fSAXParser.grammar != null) {
                        throw new SAXNotSupportedException(
                                SAXMessageFormatter.formatMessage(fConfiguration.getLocale(), "schema-already-specified", new Object[] {name}));
                    }
                    String val = (String)getProperty(JAXP_SCHEMA_LANGUAGE);
                    if ( val != null && W3C_XML_SCHEMA.equals(val) ) {
                        if (!fInitProperties.containsKey(JAXP_SCHEMA_SOURCE)) {
                            fInitProperties.put(JAXP_SCHEMA_SOURCE, super.getProperty(JAXP_SCHEMA_SOURCE));
                        }
                        super.setProperty(name, value);
                    }
                    else {
                        throw new SAXNotSupportedException(
                            SAXMessageFormatter.formatMessage(fConfiguration.getLocale(), 
                            "jaxp-order-not-supported", 
                            new Object[] {JAXP_SCHEMA_LANGUAGE, JAXP_SCHEMA_SOURCE}));
                    }
                    return;
                }
            }
            if (!fInitProperties.containsKey(name)) {
                fInitProperties.put(name, super.getProperty(name));
            }
            /** Forward property to the schema validator if there is one. **/
            if (fSAXParser != null && fSAXParser.fSchemaValidator != null) {
                setSchemaValidatorProperty(name, value);
            }
            super.setProperty(name, value);
        }
        
        public synchronized Object getProperty(String name)
            throws SAXNotRecognizedException, SAXNotSupportedException {
            if (name == null) {
                // TODO: Add localized error message.
                throw new NullPointerException();
            }
            if (fSAXParser != null && JAXP_SCHEMA_LANGUAGE.equals(name)) {
                // JAXP 1.2 support
                return fSAXParser.schemaLanguage;
            }
            return super.getProperty(name);
        }
        
        synchronized void restoreInitState()
            throws SAXNotRecognizedException, SAXNotSupportedException {
            Iterator<?> iter;
            if (!fInitFeatures.isEmpty()) {
                iter = fInitFeatures.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry<?, ?> entry = (Map.Entry<?, ?>) iter.next();
                    String name = (String) entry.getKey();
                    boolean value = ((Boolean) entry.getValue()).booleanValue();
                    super.setFeature(name, value);
                }
                fInitFeatures.clear();
            }
            if (!fInitProperties.isEmpty()) {
                iter = fInitProperties.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry<?, ?> entry = (Map.Entry<?, ?>) iter.next();
                    String name = (String) entry.getKey();
                    Object value = entry.getValue();
                    super.setProperty(name, value);
                }
                fInitProperties.clear();
            }
        }
        
        public void parse(InputSource inputSource)
            throws SAXException, IOException {
            if (fSAXParser != null && fSAXParser.fSchemaValidator != null) {
                if (fSAXParser.fSchemaValidationManager != null) {
                    fSAXParser.fSchemaValidationManager.reset();
                    fSAXParser.fUnparsedEntityHandler.reset();
                }
                resetSchemaValidator();
            }
            super.parse(inputSource);
        }
        
        public void parse(String systemId) 
            throws SAXException, IOException {
            if (fSAXParser != null && fSAXParser.fSchemaValidator != null) {
                if (fSAXParser.fSchemaValidationManager != null) {
                    fSAXParser.fSchemaValidationManager.reset();
                    fSAXParser.fUnparsedEntityHandler.reset();
                }
                resetSchemaValidator();
            }
            super.parse(systemId);
        }
        
        XMLParserConfiguration getXMLParserConfiguration() {
            return fConfiguration;
        }
        
        void setFeature0(String name, boolean value)
            throws SAXNotRecognizedException, SAXNotSupportedException {
            super.setFeature(name, value);
        }
        
        boolean getFeature0(String name)
            throws SAXNotRecognizedException, SAXNotSupportedException {
            return super.getFeature(name);
        }
        
        void setProperty0(String name, Object value)
            throws SAXNotRecognizedException, SAXNotSupportedException {
            super.setProperty(name, value);
        }
        
        Object getProperty0(String name)
            throws SAXNotRecognizedException, SAXNotSupportedException {
            return super.getProperty(name);
        }
        
        private void setSchemaValidatorFeature(String name, boolean value)
            throws SAXNotRecognizedException, SAXNotSupportedException {
            try {
                fSAXParser.fSchemaValidator.setFeature(name, value);
            }
            // This should never be thrown from the schema validator.
            catch (XMLConfigurationException e) {
                String identifier = e.getIdentifier();
                if (e.getType() == XMLConfigurationException.NOT_RECOGNIZED) {
                    throw new SAXNotRecognizedException(
                        SAXMessageFormatter.formatMessage(fConfiguration.getLocale(), 
                        "feature-not-recognized", new Object [] {identifier}));
                }
                else {
                    throw new SAXNotSupportedException(
                        SAXMessageFormatter.formatMessage(fConfiguration.getLocale(), 
                        "feature-not-supported", new Object [] {identifier}));
                }
            }
        }
        
        private void setSchemaValidatorProperty(String name, Object value) 
            throws SAXNotRecognizedException, SAXNotSupportedException {
            try {
                fSAXParser.fSchemaValidator.setProperty(name, value);
            }
            // This should never be thrown from the schema validator.
            catch (XMLConfigurationException e) {
                String identifier = e.getIdentifier();
                if (e.getType() == XMLConfigurationException.NOT_RECOGNIZED) {
                    throw new SAXNotRecognizedException(
                        SAXMessageFormatter.formatMessage(fConfiguration.getLocale(), 
                        "property-not-recognized", new Object [] {identifier}));
                }
                else {
                    throw new SAXNotSupportedException(
                        SAXMessageFormatter.formatMessage(fConfiguration.getLocale(), 
                        "property-not-supported", new Object [] {identifier}));
                }
            }
        }
        
        private void resetSchemaValidator() throws SAXException {
            try {
                fSAXParser.fSchemaValidator.reset(fSAXParser.fSchemaValidatorComponentManager);
            }
            // This should never be thrown from the schema validator.
            catch (XMLConfigurationException e) {
                throw new SAXException(e);
            }
        }
    }
}
