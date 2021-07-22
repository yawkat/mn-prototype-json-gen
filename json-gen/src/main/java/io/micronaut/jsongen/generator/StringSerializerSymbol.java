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

import com.squareup.javapoet.CodeBlock;
import io.micronaut.inject.ast.ClassElement;

import static io.micronaut.jsongen.generator.Names.DECODER;
import static io.micronaut.jsongen.generator.Names.ENCODER;

final class StringSerializerSymbol implements SerializerSymbol {
    static final StringSerializerSymbol INSTANCE = new StringSerializerSymbol();

    private StringSerializerSymbol() {
    }

    @Override
    public boolean canSerialize(ClassElement type) {
        return type.isAssignable(String.class);
    }

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
