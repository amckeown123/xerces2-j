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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.events.Namespace;

/**
 * @xerces.internal
 * 
 * @author Lucian Holland
 * @author Michael Glavassevich, IBM
 * 
 * @version $Id$
 */
abstract class ElementImpl extends XMLEventImpl {
    
    /**
     * The qualified name of the element that is being closed.
     */
    private final QName fName;
    
    /**
     * Namespaces declared in the current scope.
     */
    private final List<Namespace> fNamespaces;
    
    /**
     * Constructor.
     */
    ElementImpl(final QName name, final boolean isStartElement, Iterator<?> namespaces, final Location location) {
        super(isStartElement ? START_ELEMENT : END_ELEMENT, location);
        fName = name;
        if (namespaces != null && namespaces.hasNext()) {
            fNamespaces = new ArrayList<Namespace>();
            do {
                Namespace ns = (Namespace) namespaces.next();
                fNamespaces.add(ns);
            }
            while (namespaces.hasNext());
        }
        else {
            fNamespaces = Collections.emptyList();
        }
    }
    
    /**
     * @see javax.xml.stream.events.StartElement#getName()
     * @see javax.xml.stream.events.EndElement#getName()
     */
    public final QName getName() {
        return fName;
    }
    
    /**
     * @see javax.xml.stream.events.StartElement#getNamespaces()
     * @see javax.xml.stream.events.EndElement#getNamespaces()
     */
    @SuppressWarnings("unchecked")
    public final Iterator<?> getNamespaces() {
        return createImmutableIterator((Collection<Namespace>) fNamespaces.iterator());
    }
    
    static Iterator<?> createImmutableIterator(Collection<Namespace> col) {
        return new NoRemoveIterator(col.iterator());
    }
    
    private static final class NoRemoveIterator implements Iterator<Object> {
        
        private final Iterator<Namespace> fWrapped;
        
        public NoRemoveIterator(Iterator<Namespace> wrapped) {
            fWrapped = wrapped;
        }
        
        /**
         * @see java.util.Iterator#hasNext()
         */
        public boolean hasNext() {
            return fWrapped.hasNext();
        }

        /**
         * @see java.util.Iterator#next()
         */
        public Object next() {
            return fWrapped.next();
        }

        /**
         * @see java.util.Iterator#remove()
         */
        public void remove() {
            throw new UnsupportedOperationException("Attributes iterator is read-only.");
        }
    }
}
