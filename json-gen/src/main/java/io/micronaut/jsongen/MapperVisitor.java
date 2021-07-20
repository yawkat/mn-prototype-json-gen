package io.micronaut.jsongen;

import com.fasterxml.jackson.annotation.JacksonAnnotation;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

public class MapperVisitor implements TypeElementVisitor<Object, Object> {
    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (element.hasStereotype(JacksonAnnotation.class)) {
        }
        TypeElementVisitor.super.visitClass(element, context);
    }
}
