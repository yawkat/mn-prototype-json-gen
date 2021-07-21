package io.micronaut.jsongen;

import com.fasterxml.jackson.annotation.JacksonAnnotation;
import io.micronaut.annotation.processing.visitor.JavaVisitorContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.jsongen.generator.SerializerLinker;
import io.micronaut.jsongen.generator.SerializerSymbol;
import io.micronaut.jsongen.generator.SingletonSerializerGenerator;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;

public class MapperVisitor implements TypeElementVisitor<Object, Object> {
    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (element.hasStereotype(JacksonAnnotation.class)) {
            SerializerLinker linker = new SerializerLinker();
            SerializerSymbol symbol = linker.findSymbolForSerialize(element); // todo: move gen logic
            SingletonSerializerGenerator.GenerationResult result = SingletonSerializerGenerator.generate(element, symbol);
            try {
                // TODO: groovy context support
                JavaFileObject sourceFile = ((JavaVisitorContext) context).getProcessingEnv().getFiler().createSourceFile(result.serializerClassName.reflectionName());
                try (Writer writer = sourceFile.openWriter()) {
                    result.generatedFile.writeTo(writer);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
