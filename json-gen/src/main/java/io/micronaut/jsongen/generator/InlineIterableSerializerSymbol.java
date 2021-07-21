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
    protected static final String INTERMEDIATE = "collection";

    private final SerializerLinker linker;

    InlineIterableSerializerSymbol(SerializerLinker linker) {
        this.linker = linker;
    }

    @NonNull
    protected abstract ClassElement getElementType(ClassElement type);

    @Override
    public CodeBlock serialize(ClassElement type, CodeBlock readExpression) {
        ClassElement elementType = getElementType(type);
        SerializerSymbol elementSerializer = linker.findSymbolForSerialize(elementType);
        return CodeBlock.builder()
                .addStatement("$N.writeStartArray()", ENCODER)
                .beginControlFlow("for ($T item : $L)", PoetUtil.toTypeName(elementType), readExpression)
                .add(elementSerializer.serialize(elementType, CodeBlock.of("item")))
                .endControlFlow()
                .addStatement("$N.writeEndArray()", ENCODER)
                .build();
    }

    @Override
    public DeserializationCode deserialize(ClassElement type) {
        ClassElement elementType = getElementType(type);
        SerializerSymbol elementDeserializer = linker.findSymbolForDeserialize(elementType);

        CodeBlock.Builder block = CodeBlock.builder();
        block.add("if ($N.currentToken() != $T.START_ARRAY) throw new $T();\n", DECODER, JsonToken.class, IOException.class); // TODO: error msg
        block.add(createIntermediate(elementType));
        block.beginControlFlow("while ($N.nextToken() != $T.END_ARRAY)", DECODER, JsonToken.class);

        DeserializationCode elementDeserCode = elementDeserializer.deserialize(elementType);
        block.add(elementDeserCode.getStatements());
        // todo: name collision on nested lists?
        block.addStatement("$N.add($L)", INTERMEDIATE, elementDeserCode.getResultExpression());

        block.endControlFlow();
        return new DeserializationCode(
                block.build(),
                finishDeserialize(elementType)
        );
    }

    protected CodeBlock createIntermediate(ClassElement elementType) {
        return CodeBlock.of("$T<$T> $N = new $T<>();", ArrayList.class, PoetUtil.toTypeName(elementType), INTERMEDIATE, ArrayList.class);
    }

    protected abstract CodeBlock finishDeserialize(ClassElement elementType);

    static class ArrayImpl extends InlineIterableSerializerSymbol {
        ArrayImpl(SerializerLinker linker) {
            super(linker);
        }

        @Override
        @NonNull
        protected ClassElement getElementType(ClassElement type) {
            return type.fromArray();
        }

        @Override
        protected CodeBlock finishDeserialize(ClassElement elementType) {
            return CodeBlock.of("$N.toArray(new $T[0])", INTERMEDIATE, PoetUtil.toTypeName(elementType));
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
        protected CodeBlock finishDeserialize(ClassElement elementType) {
            return CodeBlock.of(INTERMEDIATE);
        }
    }
}
