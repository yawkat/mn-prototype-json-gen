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
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.jsongen.Serializer;

class InjectingSerializerSymbol implements SerializerSymbol {
    @Override
    public boolean canSerialize(ClassElement type) {
        // no generics of primitive types!
        return type.isArray() || !type.isPrimitive();
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
        return generatorContext.requestInjection(ParameterizedTypeName.get(ClassName.get(Serializer.class), PoetUtil.toTypeName(type))).getAccessExpression();
    }
}
