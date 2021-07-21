package io.micronaut.jsongen.generator.bean;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;

import java.util.List;

class BeanDefinition {
    MethodElement defaultConstructor;
    List<Property> props;

    static class Property {
        final String name;

        @Nullable
        FieldElement field = null;
        @Nullable
        MethodElement getter = null;
        @Nullable
        MethodElement setter = null;

        Property(String name) {
            this.name = name;
        }
    }
}
