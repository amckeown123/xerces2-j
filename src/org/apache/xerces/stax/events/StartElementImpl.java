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

package org.apache.xerces.stax.events;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Namespace;
import javax.xml.stream.events.StartElement;

import org.apache.xerces.stax.DefaultNamespaceContext;

/**
 * @xerces.internal
 * 
 * @author Lucian Holland
 *
 * @version $Id$
 */
public final class StartElementImpl extends ElementImpl implements StartElement {
    
    private static final Comparator<Object> QNAME_COMPARATOR = new Comparator<Object>() {
        public int compare(Object o1, Object o2) {
            if (o1.equals(o2)) {
                return 0;
            }
            QName name1 = (QName) o1;
            QName name2 = (QName) o2;
            return name1.toString().compareTo(name2.toString());
        }};

    private final Map<QName, Attribute> fAttributes;
    private final NamespaceContext fNamespaceContext;

    /**
     * @param location
     * @param schemaType
     */
    @SuppressWarnings({ })
    public StartElementImpl(final QName name, final Iterator<?> attributes, final Iterator<?> namespaces, final NamespaceContext namespaceContext, final Location location) {
        super(name, true, namespaces, location);
        if (attributes != null && attributes.hasNext()) {
            fAttributes = new TreeMap<QName, Attribute>(QNAME_COMPARATOR);
            do {
                Attribute attr = (Attribute) attributes.next();
                fAttributes.put((QName) attr.getName(), attr);
            }
            while (attributes.hasNext());
        }
        else {
            fAttributes = Collections.emptyMap();
        }
        fNamespaceContext = (namespaceContext != null) ? namespaceContext : DefaultNamespaceContext.getInstance();
    }

    /**
     * @see javax.xml.stream.events.StartElement#getAttributes()
     */
    public Iterator<?> getAttributes() {
        return fAttributes.values().iterator();
    }

    /**
     * @see javax.xml.stream.events.StartElement#getAttributeByName(javax.xml.namespace.QName)
     */
    public Attribute getAttributeByName(final QName name) {
        return (Attribute) fAttributes.get(name);
    }

    /**
     * @see javax.xml.stream.events.StartElement#getNamespaceContext()
     */
    public NamespaceContext getNamespaceContext() {
        return fNamespaceContext;
    }

    /**
     * @see javax.xml.stream.events.StartElement#getNamespaceURI(java.lang.String)
     */
    public String getNamespaceURI(final String prefix) {
        return fNamespaceContext.getNamespaceURI(prefix);
    }
    
    public void writeAsEncodedUnicode(Writer writer) throws XMLStreamException {
        try {
            // Write start tag.
            writer.write('<');
            QName name = getName();
            String prefix = name.getPrefix();
            if (prefix != null && prefix.length() > 0) {
                writer.write(prefix);
                writer.write(':');
            }
            writer.write(name.getLocalPart());
            // Write namespace declarations.
            Iterator<?> nsIter = getNamespaces();
            while (nsIter.hasNext()) {
                Namespace ns = (Namespace) nsIter.next();
                writer.write(' ');
                ns.writeAsEncodedUnicode(writer);
            }
            // Write attributes
            Iterator<?> attrIter = getAttributes();
            while (attrIter.hasNext()) {
                Attribute attr = (Attribute) attrIter.next();
                writer.write(' ');
                attr.writeAsEncodedUnicode(writer);
            }
            writer.write('>');
        }
        catch (IOException ioe) {
            throw new XMLStreamException(ioe);
        }
    }
}
