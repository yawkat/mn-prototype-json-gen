package io.micronaut.jsongen.generator;

import com.fasterxml.jackson.core.JsonToken;
import com.squareup.javapoet.CodeBlock;
import io.micronaut.inject.ast.ClassElement;

import java.io.IOException;
import java.util.List;

import static io.micronaut.jsongen.generator.Names.DECODER;

/**
 * {@link SerializerSymbol} that deserializes iterables (and arrays) inline, i.e. without a separate {@link io.micronaut.jsongen.Serializer} implementation.
 */
abstract class InlineIterableSerializerSymbol implements SerializerSymbol {
    protected static final String INTERMEDIATE = "collection";

    private final SerializerLinker linker;

    InlineIterableSerializerSymbol(SerializerLinker linker) {
        this.linker = linker;
    }

    protected abstract ClassElement getElementType(ClassElement type);

    @Override
    public CodeBlock serialize(ClassElement type, CodeBlock readExpression) {
        ClassElement elementType = getElementType(type);
        SerializerSymbol elementSerializer = linker.findSymbolForSerialize(elementType);
        return CodeBlock.builder()
                .beginControlFlow("for ($T item : " + readExpression + ")", PoetUtil.toTypeName(elementType))
                .add(elementSerializer.serialize(elementType, CodeBlock.of("item")))
                .endControlFlow()
                .build();
    }

    @Override
    public DeserializationCode deserialize(ClassElement type) {
        ClassElement elementType = getElementType(type);
        SerializerSymbol elementDeserializer = linker.findSymbolForDeserialize(elementType);

        CodeBlock.Builder block = CodeBlock.builder();
        block.add("if ($N.currentToken() != $T.START_ARRAY) throw $T()", DECODER, JsonToken.class, IOException.class); // TODO: error msg
        block.add(createIntermediate(elementType));
        block.addStatement("$T<$T> list = new $T<>()", ArrayList.class, PoetUtil.toTypeName(elementType), ArrayList.class);
        block.beginControlFlow("while ($N.nextToken() != $T.END_ARRAY)", DECODER, JsonToken.class);

        DeserializationCode elementDeserCode = elementDeserializer.deserialize(elementType);
        block.add(elementDeserCode.getStatements());
        // todo: name collision on nested lists?
        block.addStatement("list.add(" + elementDeserCode.getResultExpression() + ")");

        block.endControlFlow();
        return new DeserializationCode(
                block.build(),
                finishDeserialize(elementType)
        );
    }

    protected CodeBlock createIntermediate(ClassElement elementType) {
        return CodeBlock.of("$T<$T> $N = new $T<>();", List.class, ArrayList.class);
    }

    protected abstract CodeBlock finishDeserialize(ClassElement elementType);

    static class Array extends InlineIterableSerializerSymbol {
        Array(SerializerLinker linker) {
            super(linker);
        }

        @Override
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
    static class ArrayList extends InlineIterableSerializerSymbol {
        ArrayList(SerializerLinker linker) {
            super(linker);
        }

        @Override
        protected ClassElement getElementType(ClassElement type) {
            return type.getTypeArguments(Iterable.class).get("T");
        }

        @Override
        protected CodeBlock finishDeserialize(ClassElement elementType) {
            return CodeBlock.of(INTERMEDIATE);
        }
    }
}
