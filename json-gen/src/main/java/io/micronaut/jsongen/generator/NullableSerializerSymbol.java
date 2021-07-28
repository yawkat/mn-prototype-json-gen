package io.micronaut.jsongen.generator;

import com.fasterxml.jackson.core.JsonToken;
import com.squareup.javapoet.CodeBlock;
import io.micronaut.inject.ast.ClassElement;

public class NullableSerializerSymbol implements SerializerSymbol {
    private final SerializerSymbol delegate;

    public NullableSerializerSymbol(SerializerSymbol delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean canSerialize(ClassElement type) {
        throw new UnsupportedOperationException("Not part of the normal linker chain");
    }

    @Override
    public SerializerSymbol withRecursiveSerialization() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitDependencies(DependencyVisitor visitor, ClassElement type) {
        delegate.visitDependencies(visitor, type);
    }

    @Override
    public CodeBlock serialize(GeneratorContext generatorContext, ClassElement type, CodeBlock readExpression) {
        String variable = generatorContext.newLocalVariable("tmp");
        return CodeBlock.builder()
                .addStatement("$T $N = $L", PoetUtil.toTypeName(type), variable, readExpression)
                .beginControlFlow("if ($N == null)", variable)
                .addStatement("$N.writeNull()", Names.ENCODER)
                .nextControlFlow("else")
                .add(delegate.serialize(generatorContext, type, CodeBlock.of("$N", variable)))
                .endControlFlow()
                .build();
    }

    @Override
    public CodeBlock deserialize(GeneratorContext generatorContext, ClassElement type, Setter setter) {
        return CodeBlock.builder()
                .beginControlFlow("if ($N.currentToken() == $T.VALUE_NULL)", Names.DECODER, JsonToken.class)
                .add(setter.createSetStatement(CodeBlock.of("null")))
                .nextControlFlow("else")
                .add(delegate.deserialize(generatorContext, type, setter))
                .endControlFlow()
                .build();
    }
}
