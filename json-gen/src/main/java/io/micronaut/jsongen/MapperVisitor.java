package io.micronaut.jsongen;

import com.fasterxml.jackson.annotation.JacksonAnnotation;
import com.squareup.javapoet.JavaFile;
import io.micronaut.annotation.processing.visitor.JavaVisitorContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.jsongen.bean.BeanSerializerGenerator;
import io.micronaut.jsongen.generator.SerializerLinker;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;

public class MapperVisitor implements TypeElementVisitor<Object, Object> {
    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (element.hasStereotype(JacksonAnnotation.class)) {
            BeanSerializerGenerator generator = new BeanSerializerGenerator(new SerializerLinker(), element);
            JavaFile serializerFile = generator.generate();
            try {
                // TODO: groovy context support
                JavaFileObject sourceFile = ((JavaVisitorContext) context).getProcessingEnv().getFiler().createSourceFile(generator.getQualifiedName().toString());
                try (Writer writer = sourceFile.openWriter()) {
                    writer.write(serializerFile.toString());
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
