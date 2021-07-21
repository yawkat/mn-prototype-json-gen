package io.micronaut.jsongen.generator;

import com.squareup.javapoet.CodeBlock;
import io.micronaut.inject.ast.ClassElement;

import static io.micronaut.jsongen.generator.Names.DECODER;
import static io.micronaut.jsongen.generator.Names.ENCODER;

class StringSerializerSymbol implements SerializerSymbol {
    static final StringSerializerSymbol INSTANCE = new StringSerializerSymbol();

    private StringSerializerSymbol() {}

    @Override
    public CodeBlock serialize(GeneratorContext generatorContext, ClassElement type, CodeBlock readExpression) {
        // todo: handle charsequence
        return CodeBlock.of("$N.writeString($L);\n", ENCODER, readExpression);
    }

    @Override
    public DeserializationCode deserialize(GeneratorContext generatorContext, ClassElement type) {
        return new DeserializationCode(CodeBlock.of("$N.getText()", DECODER));
    }
}
