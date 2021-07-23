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

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;

import java.util.List;
import java.util.Objects;

class BeanDefinition {
    MethodElement creator;
    List<Property> creatorProps;

    List<Property> props;

    static class Property {
        final String name;

        // exactly one of these is not null
        final FieldElement field;
        final MethodElement getter;
        final MethodElement setter;
        final ParameterElement creatorParameter;

        private Property(String name, FieldElement field, MethodElement getter, MethodElement setter, ParameterElement creatorParameter) {
            this.name = name;
            this.field = field;
            this.getter = getter;
            this.setter = setter;
            this.creatorParameter = creatorParameter;
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
                throw new AssertionError("Cannot determine type, this property should have been filtered out during introspection");
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
