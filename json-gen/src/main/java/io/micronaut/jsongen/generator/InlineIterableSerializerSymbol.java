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

import com.fasterxml.jackson.core.JsonToken;
import com.squareup.javapoet.CodeBlock;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.jsongen.JsonParseException;

import java.util.ArrayList;

import static io.micronaut.jsongen.generator.Names.DECODER;
import static io.micronaut.jsongen.generator.Names.ENCODER;

/**
 * {@link SerializerSymbol} that deserializes iterables (and arrays) inline, i.e. without a separate
 * {@link io.micronaut.jsongen.Serializer} implementation.
 */
abstract class InlineIterableSerializerSymbol implements SerializerSymbol {
    private final SerializerLinker linker;

    InlineIterableSerializerSymbol(SerializerLinker linker) {
        this.linker = linker;
    }

    @NonNull
    protected abstract ClassElement getElementType(ClassElement type);

    @Override
    public void visitDependencies(DependencyVisitor visitor, ClassElement type) {
        if (visitor.visitStructure()) {
            ClassElement elementType = getElementType(type);
            visitor.visitStructureElement(linker.findSymbol(elementType), elementType, null);
        }
    }

    @Override
    public SerializerSymbol withRecursiveSerialization() {
        // TODO
        return SerializerSymbol.super.withRecursiveSerialization();
    }

    @Override
    public CodeBlock serialize(GeneratorContext generatorContext, ClassElement type, CodeBlock readExpression) {
        ClassElement elementType = getElementType(type);
        SerializerSymbol elementSerializer = linker.findSymbol(elementType);
        return CodeBlock.builder()
                .addStatement("$N.writeStartArray()", ENCODER)
                .beginControlFlow("for ($T item : $L)", PoetUtil.toTypeName(elementType), readExpression)
                .add(elementSerializer.serialize(generatorContext.withSubPath("[*]"), elementType, CodeBlock.of("item")))
                .endControlFlow()
                .addStatement("$N.writeEndArray()", ENCODER)
                .build();
    }

    @Override
    public CodeBlock deserialize(GeneratorContext generatorContext, ClassElement type, Setter setter) {
        ClassElement elementType = getElementType(type);
        SerializerSymbol elementDeserializer = linker.findSymbol(elementType);

        String intermediateVariable = generatorContext.newLocalVariable("intermediate");

        CodeBlock.Builder block = CodeBlock.builder();
        block.add("if ($N.currentToken() != $T.START_ARRAY) throw $T.from($N, \"Unexpected token \" + $N.currentToken() + \", expected START_OBJECT\");\n", DECODER, JsonToken.class, JsonParseException.class, DECODER, DECODER);
        block.add(createIntermediate(elementType, intermediateVariable));
        block.beginControlFlow("while ($N.nextToken() != $T.END_ARRAY)", DECODER, JsonToken.class);
        block.add(elementDeserializer.deserialize(generatorContext, elementType, expr -> CodeBlock.of("$N.add($L);\n", intermediateVariable, expr)));
        block.endControlFlow();
        block.add(setter.createSetStatement(finishDeserialize(elementType, intermediateVariable)));
        return block.build();
    }

    protected CodeBlock createIntermediate(ClassElement elementType, String intermediateVariable) {
        return CodeBlock.of("$T<$T> $N = new $T<>();\n", ArrayList.class, PoetUtil.toTypeName(elementType), intermediateVariable, ArrayList.class);
    }

    protected abstract CodeBlock finishDeserialize(ClassElement elementType, String intermediateVariable);

    static class ArrayImpl extends InlineIterableSerializerSymbol {
        ArrayImpl(SerializerLinker linker) {
            super(linker);
        }

        @Override
        public boolean canSerialize(ClassElement type) {
            return type.isArray();
        }

        @Override
        @NonNull
        protected ClassElement getElementType(ClassElement type) {
            return type.fromArray();
        }

        @Override
        protected CodeBlock finishDeserialize(ClassElement elementType, String intermediateVariable) {
            return CodeBlock.of("$N.toArray(new $T[0])", intermediateVariable, PoetUtil.toTypeName(elementType));
        }
    }

    /**
     * Can also do {@link Iterable} and {@link java.util.List}.
     */
    static class ArrayListImpl extends InlineIterableSerializerSymbol {
        ArrayListImpl(SerializerLinker linker) {
            super(linker);
        }

        @Override
        public boolean canSerialize(ClassElement type) {
            return type.getName().equals("java.lang.Iterable") ||
                    type.getName().equals("java.util.Collection") ||
                    type.getName().equals("java.util.List") ||
                    type.getName().equals("java.util.ArrayList");
        }

        @Override
        @NonNull
        protected ClassElement getElementType(ClassElement type) {
            /* todo: bug in getTypeArguments(class)? only returns java.lang.Object
            return type.getTypeArguments(Iterable.class).get("T");
            */
            if (type.getName().equals("java.util.ArrayList")) {
                return type.getTypeArguments().get("E");
            }
            if (type.getName().equals("java.util.List")) {
                return type.getTypeArguments().get("E");
            }
            if (type.getName().equals("java.util.Collection")) {
                return type.getTypeArguments().get("E");
            }
            if (type.getName().equals("java.util.Iterable")) {
                return type.getTypeArguments().get("T");
            }

            // raw type? todo
            throw new UnsupportedOperationException("raw type");
        }

        @Override
        protected CodeBlock finishDeserialize(ClassElement elementType, String intermediateVariable) {
            return CodeBlock.of("$N", intermediateVariable);
        }
    }
}
