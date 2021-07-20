package io.micronaut.jsongen.generator;

import com.squareup.javapoet.CodeBlock;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PrimitiveElement;

import static io.micronaut.jsongen.generator.Names.DECODER;
import static io.micronaut.jsongen.generator.Names.ENCODER;

class PrimitiveSerializerSymbol implements SerializerSymbol {
    static final PrimitiveSerializerSymbol INSTANCE = new PrimitiveSerializerSymbol();

    private PrimitiveSerializerSymbol() {}

    @Override
    public CodeBlock serialize(ClassElement type, CodeBlock readExpression) {
        if (type.equals(PrimitiveElement.BOOLEAN)) {
            return CodeBlock.of(ENCODER + ".writeBoolean(" + readExpression + ");\n");
        } else {
            return CodeBlock.of(ENCODER + ".writeNumber(" + readExpression + ");\n");
        }
    }

    @Override
    public DeserializationCode deserialize(ClassElement type) {
        if (!type.isPrimitive() || type.isArray()) {
            throw new UnsupportedOperationException("This symbol can only handle primitives");
        }
        return new DeserializationCode(CodeBlock.of(deserializeExpression(type)));
    }

    private String deserializeExpression(ClassElement type) {
        if (type.equals(PrimitiveElement.BOOLEAN)) {
            return DECODER + ".getBooleanValue()";
        } else if (type.equals(PrimitiveElement.BYTE)) {
            return DECODER + ".getByteValue()";
        } else if (type.equals(PrimitiveElement.SHORT)) {
            return DECODER + ".getShortValue()";
        } else if (type.equals(PrimitiveElement.CHAR)) {
            return "(char) " + DECODER + ".getIntValue()"; // TODO
        } else if (type.equals(PrimitiveElement.INT)) {
            return DECODER + ".getIntValue()";
        } else if (type.equals(PrimitiveElement.LONG)) {
            return DECODER + ".getLongValue()";
        } else if (type.equals(PrimitiveElement.FLOAT)) {
            return DECODER + ".getFloatValue()";
        } else if (type.equals(PrimitiveElement.DOUBLE)) {
            return DECODER + ".getDoubleValue()";
        } else {
            throw new AssertionError("unknown primitive type " + type);
        }
    }
}
