package io.micronaut.jsongen.generator.bean;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;

import java.util.LinkedHashMap;
import java.util.Map;

class BeanDefinition {
    final Map<String, Property> props = new LinkedHashMap<>();

    MethodElement defaultConstructor;

    static class Property {
        final String name;

        @Nullable
        FieldElement field = null;
        @Nullable
        MethodElement getter = null;
        @Nullable
        MethodElement setter = null;
        @Nullable
        MethodElement isGetter = null;

        Property(String name) {
            this.name = name;
        }
    }
}
