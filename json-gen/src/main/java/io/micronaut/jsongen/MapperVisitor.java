package io.micronaut.jsongen;

import com.fasterxml.jackson.annotation.JacksonAnnotation;
import io.micronaut.annotation.processing.visitor.JavaVisitorContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.jsongen.generator.SerializerLinker;
import io.micronaut.jsongen.generator.SerializerSymbol;
import io.micronaut.jsongen.generator.SingletonSerializerGenerator;
import io.micronaut.jsongen.generator.bean.InlineBeanSerializerSymbol;

import javax.annotation.processing.Filer;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MapperVisitor implements TypeElementVisitor<Object, Object> {
    private static final String ATTR_LINKER = "io.micronaut.SERIALIZER_LINKER";

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (element.hasStereotype(JacksonAnnotation.class)) {
            // triggers generation logic
            getLinker(context).findSymbolGeneric(element);
        }
    }

    @Override
    public void finish(VisitorContext visitorContext) {
        GeneratingLinker linker = getLinker(visitorContext);
        if (linker.outputQueue.isEmpty()) return;

        Filer filer = ((JavaVisitorContext) visitorContext).getProcessingEnv().getFiler();
        try {
            for (SingletonSerializerGenerator.GenerationResult generationResult : linker.outputQueue) {
                JavaFileObject sourceFile = filer.createSourceFile(generationResult.serializerClassName.reflectionName());
                try (Writer writer = sourceFile.openWriter()) {
                    generationResult.generatedFile.writeTo(writer);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        // prevent duplicate output
        linker.outputQueue.clear();
    }

    private GeneratingLinker getLinker(VisitorContext context) {
        Optional<GeneratingLinker> present = context.get(ATTR_LINKER, GeneratingLinker.class);
        if (present.isPresent()) return present.get();
        GeneratingLinker linker = new GeneratingLinker();
        context.put(ATTR_LINKER, linker);
        return linker;
    }

    private static class GeneratingLinker extends SerializerLinker {
        private final List<SingletonSerializerGenerator.GenerationResult> outputQueue = new ArrayList<>();

        @Override
        protected SerializerSymbol findSymbolGeneric(ClassElement type) {
            SerializerSymbol symbol = super.findSymbolGeneric(type);
            if (symbol instanceof InlineBeanSerializerSymbol) {
                SingletonSerializerGenerator.GenerationResult result = SingletonSerializerGenerator.generate(type, symbol);
                outputQueue.add(result);
                registerSymbol(result);
                symbol = result;
            }
            return symbol;
        }
    }
}
