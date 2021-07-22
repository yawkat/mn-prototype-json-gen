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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.AnnotatedElement;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.*;

import java.util.*;
import java.util.stream.Collectors;

class BeanIntrospector {
    public static BeanDefinition introspect(ClassElement clazz, boolean forSerialization) {
        Scanner scanner = new Scanner(forSerialization);
        scanner.scan(clazz);
        BeanDefinition beanDefinition = new BeanDefinition();
        // note: this map is *not* in the right order anymore! (not a LinkedHashMap)
        Map<Property, BeanDefinition.Property> completeProps = scanner.byName.values().stream().collect(Collectors.toMap(
                prop -> prop,
                prop -> {
                    prop.trimInaccessible(forSerialization);

                    BeanDefinition.Property tgt = new BeanDefinition.Property(prop.name);
                    if (prop.getter != null) {
                        tgt.getter = prop.getter.accessor;
                    }
                    if (prop.setter != null) {
                        tgt.setter = prop.setter.accessor;
                    }
                    if (prop.field != null) {
                        tgt.field = prop.field.accessor;
                    }
                    if (prop.creatorParameter != null) {
                        tgt.creatorParameter = prop.creatorParameter;
                    }
                    return tgt;
                }
        ));
        // filter out properties based on whether they're read/write-only
        beanDefinition.props = scanner.byName.values().stream()
                .filter(e -> e.shouldInclude(forSerialization))
                .map(completeProps::get)
                .collect(Collectors.toList());
        if (scanner.creator == null) {
            if (scanner.defaultConstructor == null) {
                throw new UnsupportedOperationException("Missing default constructor or @JsonCreator");
            }

            // use the default constructor as an "empty creator"
            beanDefinition.creator = scanner.defaultConstructor;
            beanDefinition.creatorProps = Collections.emptyList();
        } else {
            beanDefinition.creator = scanner.creator;
            beanDefinition.creatorProps = scanner.creatorProps.stream().map(completeProps::get).collect(Collectors.toList());
        }
        return beanDefinition;
    }

    private static String firstExplicitName(Accessor<?>... accessors) {
        for (Accessor<?> accessor : accessors) {
            if (accessor != null && accessor.type == AccessorType.EXPLICIT) {
                return accessor.name;
            }
        }
        return null;
    }

    private static String decapitalize(String s) {
        if (s.isEmpty()) {
            return "";
        }

        char firstChar = s.charAt(0);
        if (Character.isLowerCase(firstChar)) {
            return s;
        }

        // todo: abbreviations at start of string

        return Character.toLowerCase(firstChar) + s.substring(1);
    }

    /**
     * mostly follows jackson-jr AnnotationBasedIntrospector.
     */
    private static class Scanner {
        final boolean forSerialization;

        final Map<String, Property> byImplicitName = new LinkedHashMap<>();
        Map<String, Property> byName;

        MethodElement defaultConstructor;

        MethodElement creator = null;
        List<Property> creatorProps;

        Scanner(boolean forSerialization) {
            this.forSerialization = forSerialization;
        }

        private Property getByImplicitName(String implicitName) {
            return byImplicitName.computeIfAbsent(implicitName, s -> new Property());
        }

        private Property getByName(String name) {
            return byName.computeIfAbsent(name, s -> {
                Property property = new Property();
                property.name = s;
                return property;
            });
        }

        private String getExplicitName(AnnotatedElement element) {
            AnnotationValue<JsonProperty> jsonProperty = element.getAnnotation(JsonProperty.class);
            if (jsonProperty != null) {
                return jsonProperty.getValue(String.class).orElse("");
            }
            return null;
        }

        private boolean isIgnore(AnnotatedElement element) {
            AnnotationValue<JsonIgnore> ignore = element.getAnnotation(JsonIgnore.class);
            if (ignore == null) {
                return false;
            }
            Optional<Boolean> value = ignore.getValue(Boolean.class);
            return value.orElse(true);
        }

        private <T extends Element> Accessor<T> makeAccessor(T element, String implicitName) {
            String explicitName = getExplicitName(element);
            String finalName = implicitName;
            AccessorType type;
            if (isIgnore(element)) {
                type = AccessorType.IGNORABLE;
            } else if (explicitName == null) {
                type = AccessorType.IMPLICIT;
            } else if (explicitName.isEmpty()) {
                type = AccessorType.VISIBLE;
            } else {
                type = AccessorType.EXPLICIT;
                finalName = explicitName;
            }
            return new Accessor<>(finalName, element, type);
        }

        void scan(ClassElement clazz) {
            // todo: check we don't have another candidate when replacing properties of the definition

            // note: clazz may be a superclass of our original class. in that case, the defaultConstructor will be overwritten.
            defaultConstructor = clazz.getDefaultConstructor().orElse(null);

            for (FieldElement field : clazz.getFields()) {
                if (field.isStatic()) {
                    continue;
                }

                Property prop = getByImplicitName(field.getName());
                prop.field = makeAccessor(field, field.getName());
            }

            for (MethodElement method : clazz.getEnclosedElements(ElementQuery.ALL_METHODS)) {
                if (method.isStatic()) {
                    continue;
                }

                String rawName = method.getName();
                if (method.getParameters().length == 0) {
                    // getter
                    String implicitName = null;
                    // TODO: reuse bean method detection
                    if (rawName.startsWith("get") && rawName.length() > 3) {
                        implicitName = decapitalize(rawName.substring(3));
                    } else if (rawName.startsWith("is") && rawName.length() > 2) {
                        implicitName = decapitalize(rawName.substring(2));
                    }

                    if (implicitName != null) {
                        Property prop = getByImplicitName(implicitName);
                        prop.getter = makeAccessor(method, implicitName);
                    } else {
                        if (getExplicitName(method) != null) {
                            // if we have an explicit @JsonProperty, fall back to just the method name as the implicit name
                            Property prop = getByImplicitName(method.getName());
                            prop.getter = makeAccessor(method, method.getName());
                        }
                    }
                } else if (method.getParameters().length == 1) {
                    // setter
                    String implicitName;
                    if (rawName.startsWith("set") && rawName.length() > 3) {
                        implicitName = decapitalize(rawName.substring(3));
                    } else {
                        implicitName = null;
                    }

                    if (implicitName != null) {
                        Property prop = getByImplicitName(implicitName);
                        prop.setter = makeAccessor(method, implicitName);
                    } else {
                        if (getExplicitName(method) != null) {
                            // if we have an explicit @JsonProperty, fall back to just the method name as the implicit name
                            Property prop = getByImplicitName(method.getName());
                            prop.setter = makeAccessor(method, method.getName());
                        }
                    }
                }
            }

            byName = new LinkedHashMap<>();
            for (Map.Entry<String, Property> entry : byImplicitName.entrySet()) {
                Property prop = entry.getValue();
                String explicitName = forSerialization ? firstExplicitName(prop.getter, prop.setter, prop.field) : firstExplicitName(prop.setter, prop.getter, prop.field);
                prop.name = explicitName == null ? entry.getKey() : explicitName;
                byName.put(prop.name, prop);
            }

            for (MethodElement method : clazz.getEnclosedElements(ElementQuery.ALL_METHODS.annotated(m -> m.hasAnnotation(JsonCreator.class)))) {
                handleCreator(method);
            }
            for (ConstructorElement constructor : clazz.getEnclosedElements(ElementQuery.of(ConstructorElement.class).annotated(m -> m.hasAnnotation(JsonCreator.class)))) {
                handleCreator(constructor);
            }
        }

        private void handleCreator(MethodElement method) {
            AnnotationValue<JsonCreator> creatorAnnotation = method.getAnnotation(JsonCreator.class);
            assert creatorAnnotation != null;
            JsonCreator.Mode mode = creatorAnnotation.get("mode", JsonCreator.Mode.class).orElse(JsonCreator.Mode.DEFAULT);

            ParameterElement[] parameters = method.getParameters();
            boolean delegating;
            switch (mode) {
                case DEFAULT:
                    delegating = parameters.length == 1 && parameters[0].getAnnotation(JsonProperty.class) == null;
                    break;
                case DELEGATING:
                    delegating = true;
                    break;
                case PROPERTIES:
                    delegating = false;
                    break;
                case DISABLED:
                    return; // skip this creator
                default:
                    throw new AssertionError("bad creator mode " + mode);
            }

            // do this check after checking the mode so that DISABLED creators don't lead to an error
            if (!method.isStatic() && !(method instanceof ConstructorElement)) {
                throw new UnsupportedOperationException("@JsonCreator annotation cannot be placed on instance methods");
            }

            if (delegating) {
                // todo
                throw new UnsupportedOperationException("Delegating creator not yet supported");
            } else {
                if (creator != null) {
                    throw new UnsupportedOperationException("Multiple creators configured");
                }
                creator = method;
                creatorProps = new ArrayList<>();
                for (ParameterElement parameter : parameters) {
                    AnnotationValue<JsonProperty> propertyAnnotation = parameter.getAnnotation(JsonProperty.class);
                    if (propertyAnnotation == null) {
                        throw new UnsupportedOperationException("All parameters of a @JsonCreator must be annotated with a @JsonProperty");
                    }
                    String propName = propertyAnnotation.getValue(String.class)
                            // we allow empty property names here, as long as they're explicitly defined.
                            .orElseThrow(() -> new UnsupportedOperationException("@JsonProperty name cannot be missing on a creator"));
                    Property prop = getByName(propName);
                    prop.creatorParameter = parameter;
                    creatorProps.add(prop);
                }
            }
        }
    }

    private static class Property {
        String name;

        @Nullable
        Accessor<FieldElement> field;
        @Nullable
        Accessor<MethodElement> getter;
        @Nullable
        Accessor<MethodElement> setter;
        @Nullable
        ParameterElement creatorParameter;

        void trimInaccessible(boolean forSerialization) {
            if (getter != null && !getter.isAccessible()) {
                getter = null;
            }
            if (setter != null && !setter.isAccessible()) {
                setter = null;
            }
            if (field != null && (!field.isAccessible() || (!forSerialization && field.accessor.isFinal()))) {
                field = null;
            }
        }

        boolean shouldInclude(boolean forSerialization) {
            // if the accessors weren't accessible, they were already removed in trimAccessible
            // todo: error when a property is inaccessible because the user forgot to give access
            if (forSerialization) {
                return getter != null || field != null;
            } else {
                return setter != null || field != null || creatorParameter != null;
            }
        }
    }

    private static class Accessor<T extends Element> {
        final String name;
        final T accessor;
        final AccessorType type;

        Accessor(String name, T accessor, AccessorType type) {
            this.name = name;
            this.accessor = accessor;
            this.type = type;
        }

        boolean isAccessible() {
            // serializers are always in the same package right now
            return !accessor.isPrivate();
        }
    }

    private enum AccessorType {
        /**
         * {@literal @}{@link com.fasterxml.jackson.annotation.JsonIgnore}.
         */
        IGNORABLE,
        /**
         * Looks like an accessor.
         */
        IMPLICIT,
        /**
         * {@literal @}{@link JsonProperty} without name.
         */
        VISIBLE,
        /**
         * {@literal @}{@link JsonProperty} with name.
         */
        EXPLICIT,
    }
}
