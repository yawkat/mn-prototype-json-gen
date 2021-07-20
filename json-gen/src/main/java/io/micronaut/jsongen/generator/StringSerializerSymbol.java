package io.micronaut.jsongen.generator;

import com.squareup.javapoet.CodeBlock;
import io.micronaut.inject.ast.ClassElement;

import static io.micronaut.jsongen.generator.Names.DECODER;
import static io.micronaut.jsongen.generator.Names.ENCODER;

class StringSerializerSymbol implements SerializerSymbol {
    static final StringSerializerSymbol INSTANCE = new StringSerializerSymbol();

    private StringSerializerSymbol() {}

    @Override
    public CodeBlock serialize(ClassElement type, CodeBlock readExpression) {
        // todo: handle charsequence
        return CodeBlock.of(ENCODER + ".writeString(" + readExpression + ");\n");
    }

    @Override
    public DeserializationCode deserialize(ClassElement type) {
        return new DeserializationCode(CodeBlock.of(DECODER + ".getText()"));
    }
}
