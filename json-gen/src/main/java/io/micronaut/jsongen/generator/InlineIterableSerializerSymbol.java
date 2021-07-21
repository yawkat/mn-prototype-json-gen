package io.micronaut.jsongen.generator;

import com.fasterxml.jackson.core.JsonToken;
import com.squareup.javapoet.CodeBlock;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static io.micronaut.jsongen.generator.Names.DECODER;
import static io.micronaut.jsongen.generator.Names.ENCODER;

/**
 * {@link SerializerSymbol} that deserializes iterables (and arrays) inline, i.e. without a separate
 * {@link io.micronaut.jsongen.Serializer} implementation.
 */
abstract class InlineIterableSerializerSymbol implements SerializerSymbol {
    private final SerializerLinker linker;

    InlineIterableSerializerSymbol(SerializerLinker linker) {
        this.linker = linker;
    }

    @NonNull
    protected abstract ClassElement getElementType(ClassElement type);

    @Override
    public CodeBlock serialize(GeneratorContext generatorContext, ClassElement type, CodeBlock readExpression) {
        ClassElement elementType = getElementType(type);
        SerializerSymbol elementSerializer = linker.findSymbolForSerialize(elementType);
        return CodeBlock.builder()
                .addStatement("$N.writeStartArray()", ENCODER)
                .beginControlFlow("for ($T item : $L)", PoetUtil.toTypeName(elementType), readExpression)
                .add(elementSerializer.serialize(generatorContext, elementType, CodeBlock.of("item")))
                .endControlFlow()
                .addStatement("$N.writeEndArray()", ENCODER)
                .build();
    }

    @Override
    public DeserializationCode deserialize(GeneratorContext generatorContext, ClassElement type) {
        ClassElement elementType = getElementType(type);
        SerializerSymbol elementDeserializer = linker.findSymbolForDeserialize(elementType);

        String intermediateVariable = generatorContext.newLocalVariable("intermediate");

        CodeBlock.Builder block = CodeBlock.builder();
        block.add("if ($N.currentToken() != $T.START_ARRAY) throw new $T();\n", DECODER, JsonToken.class, IOException.class); // TODO: error msg
        block.add(createIntermediate(elementType, intermediateVariable));
        block.beginControlFlow("while ($N.nextToken() != $T.END_ARRAY)", DECODER, JsonToken.class);

        DeserializationCode elementDeserCode = elementDeserializer.deserialize(generatorContext, elementType);
        block.add(elementDeserCode.getStatements());
        // todo: name collision on nested lists?
        block.addStatement("$N.add($L)", intermediateVariable, elementDeserCode.getResultExpression());

        block.endControlFlow();
        return new DeserializationCode(
                block.build(),
                finishDeserialize(elementType, intermediateVariable)
        );
    }

    protected CodeBlock createIntermediate(ClassElement elementType, String intermediateVariable) {
        return CodeBlock.of("$T<$T> $N = new $T<>();", ArrayList.class, PoetUtil.toTypeName(elementType), intermediateVariable, ArrayList.class);
    }

    protected abstract CodeBlock finishDeserialize(ClassElement elementType, String intermediateVariable);

    static class ArrayImpl extends InlineIterableSerializerSymbol {
        ArrayImpl(SerializerLinker linker) {
            super(linker);
        }

        @Override
        public boolean canSerialize(ClassElement type) {
            return type.isArray();
        }

        @Override
        @NonNull
        protected ClassElement getElementType(ClassElement type) {
            return type.fromArray();
        }

        @Override
        protected CodeBlock finishDeserialize(ClassElement elementType, String intermediateVariable) {
            return CodeBlock.of("$N.toArray(new $T[0])", intermediateVariable, PoetUtil.toTypeName(elementType));
        }
    }

    /**
     * Can also do {@link Iterable} and {@link List}
     */
    static class ArrayListImpl extends InlineIterableSerializerSymbol {
        ArrayListImpl(SerializerLinker linker) {
            super(linker);
        }

        @Override
        public boolean canSerialize(ClassElement type) {
            return type.getName().equals("java.lang.Iterable") ||
                    type.getName().equals("java.util.Collection") ||
                    type.getName().equals("java.util.List") ||
                    type.getName().equals("java.util.ArrayList");
        }

        @Override
        @NonNull
        protected ClassElement getElementType(ClassElement type) {
            /* todo: bug in getTypeArguments(class)? only returns java.lang.Object
            return type.getTypeArguments(Iterable.class).get("T");
            */
            if (type.getName().equals("java.util.ArrayList")) return type.getTypeArguments().get("E");
            if (type.getName().equals("java.util.List")) return type.getTypeArguments().get("E");
            if (type.getName().equals("java.util.Collection")) return type.getTypeArguments().get("E");
            if (type.getName().equals("java.util.Iterable")) return type.getTypeArguments().get("T");

            // raw type? todo
            throw new UnsupportedOperationException("raw type");
        }

        @Override
        protected CodeBlock finishDeserialize(ClassElement elementType, String intermediateVariable) {
            return CodeBlock.of("$N", intermediateVariable);
        }
    }
}
