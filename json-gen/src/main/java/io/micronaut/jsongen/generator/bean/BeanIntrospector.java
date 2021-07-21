package io.micronaut.jsongen.generator.bean;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.AnnotatedElement;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.ast.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

class BeanIntrospector {
    public static BeanDefinition introspect(ClassElement clazz, boolean forSerialization) {
        Scanner scanner = new Scanner(forSerialization);
        scanner.scan(clazz);
        BeanDefinition beanDefinition = new BeanDefinition();
        beanDefinition.defaultConstructor = scanner.defaultConstructor;
        beanDefinition.props = scanner.byImplicitName.values().stream()
                .map(prop -> {
                    BeanDefinition.Property tgt = new BeanDefinition.Property(prop.name);
                    if (prop.getter != null) tgt.getter = prop.getter.accessor;
                    if (prop.setter != null) tgt.setter = prop.setter.accessor;
                    if (prop.field != null) tgt.field = prop.field.accessor;
                    return tgt;
                })
                .collect(Collectors.toList());
        return beanDefinition;
    }

    /**
     * mostly follows jackson-jr AnnotationBasedIntrospector
     */
    private static class Scanner {
        private final boolean forSerialization;

        MethodElement defaultConstructor;

        final Map<String, Property> byImplicitName = new LinkedHashMap<>();

        Scanner(boolean forSerialization) {
            this.forSerialization = forSerialization;
        }

        private Property getByImplicitName(String implicitName) {
            return byImplicitName.computeIfAbsent(implicitName, s -> new Property());
        }

        private String getExplicitName(AnnotatedElement element) {
            AnnotationValue<JsonProperty> jsonProperty = element.getAnnotation(JsonProperty.class);
            if (jsonProperty != null) {
                Optional<String> value = jsonProperty.getValue(String.class);
                if (value.isPresent()) {
                    return value.get();
                }
            }
            return null;
        }

        private boolean isIgnore(AnnotatedElement element) {
            AnnotationValue<JsonIgnore> ignore = element.getAnnotation(JsonIgnore.class);
            if (ignore == null) return false;
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
            clazz.getSuperType().ifPresent(this::scan);

            // todo: check we don't have another candidate when replacing properties of the definition

            // note: clazz may be a superclass of our original class. in that case, the defaultConstructor will be overwritten.
            defaultConstructor = clazz.getDefaultConstructor().orElse(null);

            // TODO: ignore private members

            for (FieldElement field : clazz.getFields()) {
                if (field.isStatic()) continue;

                Property prop = getByImplicitName(field.getName());
                prop.field = makeAccessor(field, field.getName());
            }

            for (MethodElement method : clazz.getEnclosedElements(ElementQuery.ALL_METHODS)) {
                if (method.isStatic()) continue;

                String rawName = method.getName();
                if (method.getParameters().length == 0) {
                    // getter
                    String implicitName = null;
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

            for (Map.Entry<String, Property> entry : byImplicitName.entrySet()) {
                Property prop = entry.getValue();
                String explicitName = forSerialization ? firstExplicitName(prop.getter, prop.setter, prop.field) : firstExplicitName(prop.setter, prop.getter, prop.field);
                prop.name = explicitName == null ? entry.getKey() : explicitName;
            }
        }
    }

    private static String firstExplicitName(Accessor<?>... accessors) {
        for (Accessor<?> accessor : accessors) {
            if (accessor != null && accessor.type == AccessorType.EXPLICIT) {
                return accessor.name;
            }
        }
        return null;
    }

    private static class Property {
        String name;

        Accessor<FieldElement> field;
        Accessor<MethodElement> getter;
        Accessor<MethodElement> setter;
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
    }

    private enum AccessorType {
        /**
         * {@literal @}{@link com.fasterxml.jackson.annotation.JsonIgnore}
         */
        IGNORABLE,
        /**
         * Looks like an accessor
         */
        IMPLICIT,
        /**
         * {@literal @}{@link JsonProperty} without name
         */
        VISIBLE,
        /**
         * {@literal @}{@link JsonProperty} with name
         */
        EXPLICIT,
    }

    private static String decapitalize(String s) {
        if (s.isEmpty()) return "";

        char firstChar = s.charAt(0);
        if (Character.isLowerCase(firstChar)) return s;

        // todo: abbreviations at start of string

        return Character.toLowerCase(firstChar) + s.substring(1);
    }
}
