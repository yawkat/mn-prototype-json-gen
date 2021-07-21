package io.micronaut.jsongen.generator.bean;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;

import java.util.Optional;

class BeanIntrospector {
    public static BeanDefinition introspect(ClassElement clazz) {
        Scanner scanner = new Scanner();
        scanner.scan(clazz);
        return scanner.definition;
    }

    private static class Scanner {
        BeanDefinition definition = new BeanDefinition();

        void scan(ClassElement clazz) {
            clazz.getSuperType().ifPresent(this::scan);

            // todo: check we don't have another candidate when replacing properties of the definition

            // note: clazz may be a superclass of our original class. in that case, the defaultConstructor will be overwritten.
            definition.defaultConstructor = clazz.getDefaultConstructor().orElse(null);

            for (FieldElement field : clazz.getFields()) {
                AnnotationValue<JsonProperty> jsonProperty = field.getAnnotation(JsonProperty.class);
                String name = field.getName();
                if (jsonProperty != null) {
                    Optional<String> value = jsonProperty.getValue(String.class);
                    if (value.isPresent()) {
                        name = value.get();
                    }
                }
                BeanDefinition.Property property = definition.props.computeIfAbsent(name, BeanDefinition.Property::new);
                property.field = field;
            }

            for (MethodElement method : clazz.getEnclosedElements(ElementQuery.ALL_METHODS)) {
                String rawName = method.getName();
                if (rawName.startsWith("get")) {
                    String name = decapitalize(rawName.substring(3));
                    definition.props.computeIfAbsent(name, BeanDefinition.Property::new).getter = method;
                } else if (rawName.startsWith("is")) {
                    String name = decapitalize(rawName.substring(2));
                    definition.props.computeIfAbsent(name, BeanDefinition.Property::new).isGetter = method;
                } else if (rawName.startsWith("set")) {
                    String name = decapitalize(rawName.substring(3));
                    definition.props.computeIfAbsent(name, BeanDefinition.Property::new).setter = method;
                }
            }
        }
    }

    private static String decapitalize(String s) {
        if (s.isEmpty()) return "";

        char firstChar = s.charAt(0);
        if (Character.isLowerCase(firstChar)) return s;

        // todo: abbreviations at start of string

        return Character.toLowerCase(firstChar) + s.substring(1);
    }
}
