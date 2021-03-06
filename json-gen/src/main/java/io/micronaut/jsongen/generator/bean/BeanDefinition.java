/*
 * Copyright 2017-2021 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.jsongen.generator.bean;

import io.micronaut.inject.ast.*;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

class BeanDefinition {
    boolean ignoreUnknownProperties;

    MethodElement creator;
    List<Property> creatorProps;
    /**
     * If the creator is delegating, the property for the single parameter.
     */
    Property creatorDelegatingProperty;

    /**
     * If serialization should be delegating (@JsonValue), the property to use as the value.
     */
    Property valueProperty;

    List<Property> props;

    final static class Property {
        final String name;

        // exactly one of these is not null
        final FieldElement field;
        final MethodElement getter;
        final MethodElement setter;
        final ParameterElement creatorParameter;

        final boolean permitRecursiveSerialization;
        final boolean nullable;
        final boolean unwrapped;

        final Set<String> aliases;

        private Property(String name, FieldElement field, MethodElement getter, MethodElement setter, ParameterElement creatorParameter) {
            this(name, field, getter, setter, creatorParameter, false, false, false, Collections.emptySet());
        }

        private Property(String name, FieldElement field, MethodElement getter, MethodElement setter, ParameterElement creatorParameter, boolean permitRecursiveSerialization, boolean nullable, boolean unwrapped, Set<String> aliases) {
            this.name = name;
            this.field = field;
            this.getter = getter;
            this.setter = setter;
            this.creatorParameter = creatorParameter;
            this.permitRecursiveSerialization = permitRecursiveSerialization;
            this.nullable = nullable;
            this.unwrapped = unwrapped;
            this.aliases = aliases;
        }

        public Property withPermitRecursiveSerialization(boolean value) {
            return new Property(name, field, getter, setter, creatorParameter, value, nullable, unwrapped, aliases);
        }

        public Property withNullable(boolean value) {
            return new Property(name, field, getter, setter, creatorParameter, permitRecursiveSerialization, value, unwrapped, aliases);
        }

        public Property withUnwrapped(boolean value) {
            return new Property(name, field, getter, setter, creatorParameter, permitRecursiveSerialization, nullable, value, aliases);
        }

        public Property withAliases(Set<String> value) {
            return new Property(name, field, getter, setter, creatorParameter, permitRecursiveSerialization, nullable, unwrapped, value);
        }

        public ClassElement getType() {
            if (getter != null) {
                return getter.getGenericReturnType();
            } else if (setter != null) {
                return setter.getParameters()[0].getGenericType();
            } else if (field != null) {
                return field.getGenericType();
            } else if (creatorParameter != null) {
                return creatorParameter.getGenericType();
            } else {
                throw new AssertionError();
            }
        }

        /**
         * The element corresponding to this property. Used for warning messages.
         */
        public Element getElement() {
            if (getter != null) {
                return getter;
            } else if (setter != null) {
                return setter;
            } else if (field != null) {
                return field;
            } else if (creatorParameter != null) {
                return creatorParameter;
            } else {
                throw new AssertionError();
            }
        }

        static Property field(String name, FieldElement field) {
            Objects.requireNonNull(field, "field");
            return new Property(name, field, null, null, null);
        }

        static Property getter(String name, MethodElement getter) {
            Objects.requireNonNull(getter, "getter");
            return new Property(name, null, getter, null, null);
        }

        static Property setter(String name, MethodElement setter) {
            Objects.requireNonNull(setter, "setter");
            return new Property(name, null, null, setter, null);
        }

        static Property creatorParameter(String name, ParameterElement creatorParameter) {
            Objects.requireNonNull(creatorParameter, "creatorParameter");
            return new Property(name, null, null, null, creatorParameter);
        }
    }
}
