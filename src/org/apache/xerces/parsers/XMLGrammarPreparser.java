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

package org.apache.xerces.parsers;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Locale;

import org.apache.xerces.impl.Constants;
import org.apache.xerces.impl.XMLEntityManager;
import org.apache.xerces.impl.XMLErrorReporter;
import org.apache.xerces.util.SymbolTable;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xni.grammars.Grammar;
import org.apache.xerces.xni.grammars.XMLGrammarDescription;
import org.apache.xerces.xni.grammars.XMLGrammarLoader;
import org.apache.xerces.xni.grammars.XMLGrammarPool;
import org.apache.xerces.xni.parser.XMLEntityResolver;
import org.apache.xerces.xni.parser.XMLErrorHandler;
import org.apache.xerces.xni.parser.XMLInputSource;

/**
 * <p> This class provides an easy way for a user to preparse grammars
 * of various types.  By default, it knows how to preparse external
 * DTD's and schemas; it provides an easy way for user applications to
 * register classes that know how to parse additional grammar types.
 * By default, it does no grammar caching; but it provides ways for
 * user applications to do so.
 *
 * @author Neil Graham, IBM
 *
 * @version $Id$
 */
public class XMLGrammarPreparser {

    //
    // Constants
    //

    // feature:  continue-after-fatal-error
    private final static String CONTINUE_AFTER_FATAL_ERROR =
        Constants.XERCES_FEATURE_PREFIX + Constants.CONTINUE_AFTER_FATAL_ERROR_FEATURE;

    /** Property identifier: symbol table. */
    protected static final String SYMBOL_TABLE =
        Constants.XERCES_PROPERTY_PREFIX + Constants.SYMBOL_TABLE_PROPERTY;

    /** Property identifier: error reporter. */
    protected static final String ERROR_REPORTER =
        Constants.XERCES_PROPERTY_PREFIX + Constants.ERROR_REPORTER_PROPERTY;

    /** Property identifier: error handler. */
    protected static final String ERROR_HANDLER =
        Constants.XERCES_PROPERTY_PREFIX + Constants.ERROR_HANDLER_PROPERTY;

    /** Property identifier: entity resolver. */
    protected static final String ENTITY_RESOLVER =
        Constants.XERCES_PROPERTY_PREFIX + Constants.ENTITY_RESOLVER_PROPERTY;

    /** Property identifier: grammar pool . */
    protected static final String GRAMMAR_POOL =
        Constants.XERCES_PROPERTY_PREFIX + Constants.XMLGRAMMAR_POOL_PROPERTY;

    // the "built-in" grammar loaders
    private static final Hashtable<String, String> KNOWN_LOADERS = new Hashtable<String, String>();

    static {
        KNOWN_LOADERS.put(XMLGrammarDescription.XML_SCHEMA,
            "org.apache.xerces.impl.xs.XMLSchemaLoader");
        KNOWN_LOADERS.put(XMLGrammarDescription.XML_DTD,
            "org.apache.xerces.impl.dtd.XMLDTDLoader");
    }

    /** Recognized properties. */
    private static final String[] RECOGNIZED_PROPERTIES = {
        SYMBOL_TABLE,
        ERROR_REPORTER,
        ERROR_HANDLER,
        ENTITY_RESOLVER,
        GRAMMAR_POOL,
    };

    // Data
    protected final SymbolTable fSymbolTable;
    protected final XMLErrorReporter fErrorReporter;
    protected XMLEntityResolver fEntityResolver;
    protected XMLGrammarPool fGrammarPool;

    protected Locale fLocale;

    // Hashtable holding our loaders
    private final Hashtable<String, XMLGrammarLoaderContainer> fLoaders;
    
    // The number of times the configuration has been modified.
    private int fModCount = 1;

    //
    // Constructors
    //

    /** Default constructor. */
    public XMLGrammarPreparser() {
        this(new SymbolTable());
    } // <init>()

    /**
     * Constructs a preparser using the specified symbol table.
     *
     * @param symbolTable The symbol table to use.
     */
    public XMLGrammarPreparser (SymbolTable symbolTable) {
        fSymbolTable = symbolTable;

        fLoaders = new Hashtable<String, XMLGrammarLoaderContainer>();
        fErrorReporter = new XMLErrorReporter();
        setLocale(Locale.getDefault());
        fEntityResolver = new XMLEntityManager();
        // those are all the basic properties...
    } // <init>(SymbolTable)

    //
    // Public methods
    //

    /*
    * Register a type of grammar to make it preparsable.   If
    * the second parameter is null, the parser will use its  built-in
    * facilities for that grammar type.
    * This should be called by the application immediately
    * after creating this object and before initializing any properties/features.
    * @param type   URI identifying the type of the grammar
    * @param loader an object capable of preparsing that type; null if the ppreparser should use built-in knowledge.
    * @return true if successful; false if no built-in knowledge of
    *       the type or if unable to instantiate the string we know about
    */
    public boolean registerPreparser(String grammarType, XMLGrammarLoader loader) {
        if(loader == null) { // none specified!
            if(KNOWN_LOADERS.containsKey(grammarType)) {
                // got one; just instantiate it...
                String loaderName = (String)KNOWN_LOADERS.get(grammarType);
                try {
                    ClassLoader cl = ObjectFactory.findClassLoader();
                    XMLGrammarLoader gl = (XMLGrammarLoader)(ObjectFactory.newInstance(loaderName, cl, true));
                    fLoaders.put(grammarType, new XMLGrammarLoaderContainer(gl));
                } catch (Exception e) {
                    return false;
                }
                return true;
            }
            return false;
        }
        // were given one
        fLoaders.put(grammarType, new XMLGrammarLoaderContainer(loader));
        return true;
    } // registerPreparser(String, XMLGrammarLoader):  boolean

    /**
     * Parse a grammar from a location identified by an
     * XMLInputSource.
     * This method also adds this grammar to the XMLGrammarPool
     *
     * @param type The type of the grammar to be constructed
     * @param is The XMLInputSource containing this grammar's
     * information
     * <strong>If a URI is included in the systemId field, the parser will not expand this URI or make it
     * available to the EntityResolver</strong>
     * @return The newly created <code>Grammar</code>.
     * @exception XNIException thrown on an error in grammar
     * construction
     * @exception IOException thrown if an error is encountered
     * in reading the file
     */
    public Grammar preparseGrammar(String type, XMLInputSource
                is) throws XNIException, IOException {
        if (fLoaders.containsKey(type)) {
            XMLGrammarLoaderContainer xglc = (XMLGrammarLoaderContainer) fLoaders.get(type);
            XMLGrammarLoader gl = xglc.loader;
            if (xglc.modCount != fModCount) {
                // make sure gl's been set up with all the "basic" properties:
                gl.setProperty(SYMBOL_TABLE, fSymbolTable);
                gl.setProperty(ENTITY_RESOLVER, fEntityResolver);
                gl.setProperty(ERROR_REPORTER, fErrorReporter);
                // potentially, not all will support this one...
                if (fGrammarPool != null) {
                    try {
                        gl.setProperty(GRAMMAR_POOL, fGrammarPool);
                    } catch(Exception e) {
                        // too bad...
                    }
                }
                xglc.modCount = fModCount;
            }
            return gl.loadGrammar(is);
        }
        return null;
    } // preparseGrammar(String, XMLInputSource):  Grammar

    /**
     * Set the locale to use for messages.
     *
     * @param locale The locale object to use for localization of messages.
     *
     * @exception XNIException Thrown if the parser does not support the
     *                         specified locale.
     */
    public void setLocale(Locale locale) {
        fLocale = locale;
        fErrorReporter.setLocale(locale);
    } // setLocale(Locale)

    /** Return the Locale the XMLGrammarLoader is using. */
    public Locale getLocale() {
        return fLocale;
    } // getLocale():  Locale


    /**
     * Sets the error handler.
     *
     * @param errorHandler The error handler.
     */
    public void setErrorHandler(XMLErrorHandler errorHandler) {
        fErrorReporter.setProperty(ERROR_HANDLER, errorHandler);
    } // setErrorHandler(XMLErrorHandler)

    /** Returns the registered error handler.  */
    public XMLErrorHandler getErrorHandler() {
        return fErrorReporter.getErrorHandler();
    } // getErrorHandler():  XMLErrorHandler

    /**
     * Sets the entity resolver.
     *
     * @param entityResolver The new entity resolver.
     */
    public void setEntityResolver(XMLEntityResolver entityResolver) {
        if (fEntityResolver != entityResolver) {
            // Overflow. We actually need to reset the 
            // modCount on every XMLGrammarLoaderContainer.
            if (++fModCount < 0) {
                clearModCounts();
            }
            fEntityResolver = entityResolver;
        }
    } // setEntityResolver(XMLEntityResolver)

    /** Returns the registered entity resolver.  */
    public XMLEntityResolver getEntityResolver() {
        return fEntityResolver;
    } // getEntityResolver():  XMLEntityResolver

    /**
     * Sets the grammar pool.
     *
     * @param grammarPool The new grammar pool.
     */
    public void setGrammarPool(XMLGrammarPool grammarPool) {
        if (fGrammarPool != grammarPool) {
            // Overflow. We actually need to reset the 
            // modCount on every XMLGrammarLoaderContainer.
            if (++fModCount < 0) {
                clearModCounts();
            }
            fGrammarPool = grammarPool;
        }
    } // setGrammarPool(XMLGrammarPool)

    /** Returns the registered grammar pool.  */
    public XMLGrammarPool getGrammarPool() {
        return fGrammarPool;
    } // getGrammarPool():  XMLGrammarPool

    // it's possible the application may want access to a certain loader to do
    // some custom work.
    public XMLGrammarLoader getLoader(String type) {
        XMLGrammarLoaderContainer xglc = (XMLGrammarLoaderContainer) fLoaders.get(type);
        return (xglc != null) ? xglc.loader : null;
    } // getLoader(String):  XMLGrammarLoader

    // set a feature.  This method tries to set it on all
    // registered loaders; it eats any resulting exceptions.  If
    // an app needs to know if a particular feature is supported
    // by a grammar loader of a particular type, it will have
    // to retrieve that loader and use the loader's setFeature method.
    public void setFeature(String featureId, boolean value) {
        Enumeration<XMLGrammarLoaderContainer> loaders = fLoaders.elements();
        while (loaders.hasMoreElements()) {
            XMLGrammarLoader gl = ((XMLGrammarLoaderContainer)loaders.nextElement()).loader;
            try {
                gl.setFeature(featureId, value);
            } catch(Exception e) {
                // eat it up...
            }
        }
        // since our error reporter is a property we set later,
        // make sure features it understands are also set.
        if(featureId.equals(CONTINUE_AFTER_FATAL_ERROR)) {
            fErrorReporter.setFeature(CONTINUE_AFTER_FATAL_ERROR, value);
        }
    } //setFeature(String, boolean)

    // set a property.  This method tries to set it on all
    // registered loaders; it eats any resulting exceptions.  If
    // an app needs to know if a particular property is supported
    // by a grammar loader of a particular type, it will have
    // to retrieve that loader and use the loader's setProperty method.
    // <p> <strong>An application should use the explicit method
    // in this class to set "standard" properties like error handler etc.</strong>
    public void setProperty(String propId, Object value) {
        Enumeration<XMLGrammarLoaderContainer> loaders = fLoaders.elements();
        while (loaders.hasMoreElements()) {
            XMLGrammarLoader gl = ((XMLGrammarLoaderContainer)loaders.nextElement()).loader;
            try {
                gl.setProperty(propId, value);
            } catch(Exception e) {
                // eat it up...
            }
        }
    } //setProperty(String, Object)

    // get status of feature in a particular loader.  This
    // catches no exceptions--including NPE's--so the application had
    // better make sure the loader exists and knows about this feature.
    // @param type type of grammar to look for the feature in.
    // @param featureId the feature string to query.
    // @return the value of the feature.
    public boolean getFeature(String type, String featureId) {
        XMLGrammarLoader gl = ((XMLGrammarLoaderContainer)fLoaders.get(type)).loader;
        return gl.getFeature(featureId);
    } // getFeature (String, String):  boolean

    // get status of property in a particular loader.  This
    // catches no exceptions--including NPE's--so the application had
    // better make sure the loader exists and knows about this property.
    // <strong>For standard properties--that will be supported
    // by all loaders--the specific methods should be queried!</strong>
    // @param type type of grammar to look for the property in.
    // @param propertyId the property string to query.
    // @return the value of the property.
    public Object getProperty(String type, String propertyId) {
        XMLGrammarLoader gl = ((XMLGrammarLoaderContainer)fLoaders.get(type)).loader;
        return gl.getProperty(propertyId);
    } // getProperty(String, String):  Object
    
    /** 
     * Container for an XMLGrammarLoader. Also holds the modCount 
     * that the XMLGrammarPreparser had the last time it was used.
     */
    static class XMLGrammarLoaderContainer {
        public final XMLGrammarLoader loader;
        public int modCount = 0;
        public XMLGrammarLoaderContainer(XMLGrammarLoader loader) {
            this.loader = loader;
        }
    }
    
    private void clearModCounts() {
        Enumeration<XMLGrammarLoaderContainer> loaders = fLoaders.elements();
        while (loaders.hasMoreElements()) {
            XMLGrammarLoaderContainer xglc = (XMLGrammarLoaderContainer) loaders.nextElement();
            xglc.modCount = 0;
        }
        fModCount = 1;
    }

    public static String[] getRecognizedProperties() {
        return RECOGNIZED_PROPERTIES;
    }
    
} // class XMLGrammarPreparser
