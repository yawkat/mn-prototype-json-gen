/*
 * Copyright 2017-2021 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.jsongen.generator;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.ParameterizedTypeName;
import io.micronaut.context.BeanProvider;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.jsongen.Serializer;

final class InjectingSerializerSymbol implements SerializerSymbol {
    static final InjectingSerializerSymbol INSTANCE = new InjectingSerializerSymbol(false);

    private static final InjectingSerializerSymbol INSTANCE_PROVIDER = new InjectingSerializerSymbol(true);

    /**
     * Whether to wrap the injection with a {@link BeanProvider}.
     */
    private final boolean provider;

    private InjectingSerializerSymbol(boolean provider) {
        this.provider = provider;
    }

    @Override
    public boolean canSerialize(ClassElement type) {
        // no generics of primitive types!
        return type.isArray() || !type.isPrimitive();
    }

    @Override
    public SerializerSymbol withRecursiveSerialization() {
        return INSTANCE_PROVIDER;
    }

    @Override
    public CodeBlock serialize(GeneratorContext generatorContext, ClassElement type, CodeBlock readExpression) {
        return CodeBlock.of("$L.serialize($N, $L);\n", getSerializerAccess(generatorContext, type), Names.ENCODER, readExpression);
    }

    @Override
    public DeserializationCode deserialize(GeneratorContext generatorContext, ClassElement type) {
        return new DeserializationCode(CodeBlock.of("$L.deserialize($N)", getSerializerAccess(generatorContext, type), Names.DECODER));
    }

    private CodeBlock getSerializerAccess(GeneratorContext generatorContext, ClassElement type) {
        ParameterizedTypeName serializerType = ParameterizedTypeName.get(ClassName.get(Serializer.class), PoetUtil.toTypeName(type));
        if (provider) {
            serializerType = ParameterizedTypeName.get(ClassName.get(BeanProvider.class), serializerType);
        }
        CodeBlock accessExpression = generatorContext.requestInjection(serializerType).getAccessExpression();
        if (provider) {
            accessExpression = CodeBlock.of("$L.get()", accessExpression);
        }
        return accessExpression;
    }
}
