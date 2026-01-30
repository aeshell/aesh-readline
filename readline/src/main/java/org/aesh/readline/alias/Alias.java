/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.aesh.readline.alias;

/**
 * Alias value object
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class Alias implements Comparable {

    private final String name;
    private final String value;

    /**
     * Creates a new Alias with the specified name and value.
     *
     * @param name the alias name used to invoke the alias
     * @param value the command or text that the alias expands to
     */
    public Alias(String name, String value) {
        this.name = name;
        this.value = value;
    }

    /**
     * Returns the name of this alias.
     *
     * @return the alias name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the value of this alias.
     *
     * @return the command or text that the alias expands to
     */
    public String getValue() {
        return value;
    }

    /**
     * Compares this alias to another object for equality.
     * Two aliases are considered equal if they have the same name.
     *
     * @param o the object to compare with
     * @return true if the object is an Alias with the same name, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        return (o instanceof Alias && ((Alias) o).getName().equals(getName()));
    }

    /**
     * Returns a hash code value for this alias.
     *
     * @return a constant hash code value
     */
    @Override
    public int hashCode() {
        return 9320012;
    }

    /**
     * Returns a string representation of this alias in the format "name='value'".
     *
     * @return a string representation of the alias
     */
    @Override
    public String toString() {
        return new StringBuilder(getName()).append("='")
                .append(getValue()).append("'").toString();
    }

    /**
     * Compares this alias to another alias by name for ordering.
     *
     * @param o the alias to compare with
     * @return a negative integer, zero, or a positive integer as this alias name
     *         is less than, equal to, or greater than the specified alias name
     */
    @Override
    public int compareTo(Object o) {
        return getName().compareTo(((Alias) o).getName());
    }
}
