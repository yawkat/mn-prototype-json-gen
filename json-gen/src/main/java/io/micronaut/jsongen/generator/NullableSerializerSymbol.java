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
    public DeserializationCode deserialize(GeneratorContext generatorContext, ClassElement type) {
        String variable = generatorContext.newLocalVariable("tmp");
        DeserializationCode delegateCode = delegate.deserialize(generatorContext, type);
        return new DeserializationCode(
                CodeBlock.builder()
                        .addStatement("$T $N", PoetUtil.toTypeName(type), variable)
                        .beginControlFlow("if ($N.currentToken() == $T.VALUE_NULL)", Names.DECODER, JsonToken.class)
                        .addStatement("$N = null", variable)
                        .nextControlFlow("else")
                        .add(delegateCode.getStatements())
                        .addStatement("$N = $L", variable, delegateCode.getResultExpression())
                        .endControlFlow()
                        .build(),
                CodeBlock.of("$N", variable)
        );
    }
}
