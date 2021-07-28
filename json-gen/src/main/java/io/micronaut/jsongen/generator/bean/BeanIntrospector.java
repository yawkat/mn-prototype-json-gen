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

import com.fasterxml.jackson.annotation.*;
import io.micronaut.core.annotation.AnnotatedElement;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.*;
import io.micronaut.jsongen.RecursiveSerialization;
import io.micronaut.jsongen.generator.ProblemReporter;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class BeanIntrospector {
    /**
     * @param problemReporter            Where to output problems
     * @param clazz                      Class to introspect
     * @param additionalAnnotationSource Additional elements that should be scanned for class-level annotations
     * @param forSerialization           Whether this introspection is intended for serialization or deserialization
     * @return The introspection result
     */
    public static BeanDefinition introspect(ProblemReporter problemReporter, ClassElement clazz, Collection<AnnotatedElement> additionalAnnotationSource, boolean forSerialization) {
        Scanner scanner = new Scanner(problemReporter, forSerialization);
        scanner.scan(clazz, additionalAnnotationSource);
        BeanDefinition beanDefinition = new BeanDefinition();
        Map<PropBuilder, BeanDefinition.Property> completeProps = new LinkedHashMap<>();
        for (PropBuilder prop : scanner.byName.values()) {
            // remove hidden accessors
            prop.trimInaccessible(forSerialization);
            // filter out properties based on whether they're read/write-only
            if (!prop.shouldInclude(forSerialization)) {
                continue;
            }
            BeanDefinition.Property built;
            if (forSerialization) {
                if (prop.getter != null) {
                    built = BeanDefinition.Property.getter(prop.name, prop.getter.accessor);
                } else {
                    assert prop.field != null;
                    built = BeanDefinition.Property.field(prop.name, prop.field.accessor);
                }
            } else {
                if (prop.creatorParameter != null) {
                    built = BeanDefinition.Property.creatorParameter(prop.name, prop.creatorParameter);
                } else if (prop.setter != null) {
                    built = BeanDefinition.Property.setter(prop.name, prop.setter.accessor);
                } else {
                    assert prop.field != null;
                    built = BeanDefinition.Property.field(prop.name, prop.field.accessor);
                }
            }
            built = built.withPermitRecursiveSerialization(prop.permitRecursiveSerialization);
            built = built.withNullable(prop.nullable);
            built = built.withUnwrapped(prop.unwrapped);
            completeProps.put(prop, built);
        }
        beanDefinition.props = new ArrayList<>(completeProps.values());
        if (scanner.creator == null) {
            if (scanner.defaultConstructor == null) {
                problemReporter.fail("Missing default constructor or @JsonCreator", clazz);
            }

            // use the default constructor as an "empty creator"
            beanDefinition.creator = scanner.defaultConstructor;
            beanDefinition.creatorProps = Collections.emptyList();
        } else {
            beanDefinition.creator = scanner.creator;
            beanDefinition.creatorProps = scanner.creatorProps.stream().map(completeProps::get).collect(Collectors.toList());
        }
        beanDefinition.ignoreUnknownProperties = scanner.ignoreUnknownProperties;
        return beanDefinition;
    }

    /**
     * mostly follows jackson-jr AnnotationBasedIntrospector.
     */
    private static class Scanner {
        final ProblemReporter problemReporter;
        final boolean forSerialization;

        final Map<String, PropBuilder> byImplicitName = new LinkedHashMap<>();
        Map<String, PropBuilder> byName;

        MethodElement defaultConstructor;

        MethodElement creator = null;
        List<PropBuilder> creatorProps;

        boolean ignoreUnknownProperties;

        Scanner(ProblemReporter problemReporter, boolean forSerialization) {
            this.problemReporter = problemReporter;
            this.forSerialization = forSerialization;
        }

        private PropBuilder getByImplicitName(String implicitName) {
            return byImplicitName.computeIfAbsent(implicitName, s -> new PropBuilder());
        }

        private PropBuilder getByName(String name) {
            return byName.computeIfAbsent(name, s -> {
                PropBuilder prop = new PropBuilder();
                prop.name = s;
                return prop;
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

        void scan(ClassElement clazz, Collection<AnnotatedElement> additionalAnnotationSource) {
            AnnotationValue<JsonIgnoreProperties> jsonIgnoreProperties = ElementUtil.getAnnotation(JsonIgnoreProperties.class, clazz, additionalAnnotationSource);
            if (jsonIgnoreProperties != null) {
                ignoreUnknownProperties = jsonIgnoreProperties.get("ignoreUnknown", Boolean.class).orElse(false);
            }

            // todo: check we don't have another candidate when replacing properties of the definition

            // note: clazz may be a superclass of our original class. in that case, the defaultConstructor will be overwritten.
            defaultConstructor = clazz.getDefaultConstructor().orElse(null);

            for (FieldElement field : clazz.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyInstance())) {
                PropBuilder prop = getByImplicitName(field.getName());
                prop.field = makeAccessor(field, field.getName());
            }

            // used to avoid visiting a method twice, once in bean properties and once in the normal pass
            // todo: find a better solution
            Set<MethodElementWrapper> visitedMethods = new HashSet<>();

            for (PropertyElement beanProperty : clazz.getBeanProperties()) {
                String implicitName = beanProperty.getName();
                PropBuilder prop = getByImplicitName(implicitName);
                beanProperty.getReadMethod().ifPresent(readMethod -> {
                    visitedMethods.add(new MethodElementWrapper(readMethod));
                    prop.getter = makeAccessor(readMethod, implicitName);
                });
                beanProperty.getWriteMethod().ifPresent(writeMethod -> {
                    visitedMethods.add(new MethodElementWrapper(writeMethod));
                    prop.setter = makeAccessor(writeMethod, implicitName);
                });
            }

            for (MethodElement method : clazz.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance())) {
                if (!visitedMethods.add(new MethodElementWrapper(method))) {
                    // skip methods we already visited for properties
                    continue;
                }

                // if we have an explicit @JsonProperty, fall back to just the method name as the implicit name
                if (method.getParameters().length == 0) {
                    // getter
                    if (getExplicitName(method) != null) {
                        PropBuilder prop = getByImplicitName(method.getName());
                        prop.getter = makeAccessor(method, method.getName());
                    }
                } else if (method.getParameters().length == 1) {
                    // setter
                    if (getExplicitName(method) != null) {
                        PropBuilder prop = getByImplicitName(method.getName());
                        prop.setter = makeAccessor(method, method.getName());
                    }
                }
            }

            byName = new LinkedHashMap<>();
            for (Map.Entry<String, PropBuilder> entry : byImplicitName.entrySet()) {
                PropBuilder prop = entry.getValue();
                String explicitName = prop.accessorsInOrder(forSerialization)
                        .filter(acc -> acc.type == AccessorType.EXPLICIT)
                        .findFirst()
                        .map(acc -> acc.name)
                        .orElse(null);
                prop.name = explicitName == null ? entry.getKey() : explicitName;
                byName.put(prop.name, prop);
            }

            for (MethodElement method : clazz.getEnclosedElements(ElementQuery.ALL_METHODS.annotated(m -> m.hasAnnotation(JsonCreator.class)))) {
                handleCreator(method);
            }
            for (ConstructorElement constructor : clazz.getEnclosedElements(ElementQuery.of(ConstructorElement.class).annotated(m -> m.hasAnnotation(JsonCreator.class)))) {
                handleCreator(constructor);
            }

            for (PropBuilder prop : byName.values()) {
                // if there's a @RecursiveSerialization on *any* of the involved elements, mark the property for recursive ser
                prop.permitRecursiveSerialization = prop.annotatedElementsInOrder(forSerialization)
                        .anyMatch(element -> element.hasAnnotation(RecursiveSerialization.class));

                // infer nullable support from the first @Nullable/@NonNull annotation we find
                prop.nullable = prop.annotatedElementsInOrder(forSerialization)
                        .map(element -> {
                            if (element.isNullable()) {
                                return true;
                            } else if (element.isNonNull()) {
                                return false;
                            } else {
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .findFirst().orElse(false);

                prop.unwrapped = prop.annotatedElementsInOrder(forSerialization)
                        .anyMatch(element -> element.hasAnnotation(JsonUnwrapped.class));

                if (prop.unwrapped && prop.permitRecursiveSerialization) {
                    //noinspection OptionalGetWithoutIsPresent
                    problemReporter.fail("Cannot combine @RecursiveSerialization with @JsonUnwrapped",
                            prop.annotatedElementsInOrder(forSerialization).findFirst().get());
                }
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
                problemReporter.fail("@JsonCreator annotation cannot be placed on instance methods", method);
                return;
            }

            if (delegating) {
                // todo
                problemReporter.fail("Delegating creator not yet supported", method);
            } else {
                if (creator != null) {
                    problemReporter.fail("Multiple creators configured", method);
                }
                creator = method;
                creatorProps = new ArrayList<>();
                for (ParameterElement parameter : parameters) {
                    AnnotationValue<JsonProperty> propertyAnnotation = parameter.getAnnotation(JsonProperty.class);
                    if (propertyAnnotation == null) {
                        problemReporter.fail("All parameters of a @JsonCreator must be annotated with a @JsonProperty", parameter);
                        continue;
                    }
                    Optional<String> propName = propertyAnnotation.getValue(String.class);
                    // we allow empty property names here, as long as they're explicitly defined.
                    if (!propName.isPresent()) {
                        problemReporter.fail("@JsonProperty name cannot be missing on a creator", parameter);
                        continue;
                    }
                    PropBuilder prop = getByName(propName.get());
                    prop.creatorParameter = parameter;
                    creatorProps.add(prop);
                }
            }
        }
    }

    private static class PropBuilder {
        String name;

        boolean permitRecursiveSerialization;
        boolean nullable;
        boolean unwrapped;

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

        Stream<Accessor<? extends MemberElement>> accessorsInOrder(boolean forSerialization) {
            return (forSerialization ? Stream.of(getter, setter, field) : Stream.of(setter, getter, field)).filter(Objects::nonNull);
        }

        /**
         * Get the elements for this property that should be scanned for annotations, in order of priority.
         */
        Stream<Element> annotatedElementsInOrder(boolean forSerialization) {
            Stream<Element> stream = accessorsInOrder(forSerialization).map(a -> a.accessor);
            if (creatorParameter != null) {
                if (forSerialization) {
                    stream = Stream.concat(stream, Stream.of(creatorParameter));
                } else {
                    stream = Stream.concat(Stream.of(creatorParameter), stream);
                }
            }
            return stream;
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
         * <p>
         * todo: actually implement this
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

    private static class MethodElementWrapper {
        private final MethodElement element;

        MethodElementWrapper(MethodElement element) {
            this.element = element;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof MethodElementWrapper && ElementUtil.equals(element, ((MethodElementWrapper) o).element);
        }

        @Override
        public int hashCode() {
            // should be fine
            return element.getName().hashCode();
        }
    }
}
