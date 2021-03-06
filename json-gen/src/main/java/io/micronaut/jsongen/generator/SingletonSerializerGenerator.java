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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.squareup.javapoet.*;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.jsongen.Serializer;
import jakarta.inject.Inject;

import javax.lang.model.element.Modifier;
import java.io.IOException;

import static io.micronaut.jsongen.generator.Names.DECODER;
import static io.micronaut.jsongen.generator.Names.ENCODER;

public final class SingletonSerializerGenerator {
    private SingletonSerializerGenerator() {
    }

    public static GenerationResult generate(ProblemReporter problemReporter, ClassElement clazz, SerializerSymbol symbol) {
        return generate(
                problemReporter,
                ClassName.get(clazz.getPackageName(), clazz.getSimpleName() + "$Serializer"),
                PoetUtil.toTypeName(clazz),
                symbol,
                clazz
        );
    }

    /**
     * @param serializerName FQCN of the generated serializer class
     * @param valueName      type name to use for the value being serialized, must be a reference type
     * @param symbol         symbol to use for serialization
     * @param valueType      type to pass to the symbol for code generation. Usually identical to {@code valueName}, except for primitives
     * @return The generated serializer class
     */
    static GenerationResult generate(
            ProblemReporter problemReporter,
            ClassName serializerName,
            TypeName valueName,
            SerializerSymbol symbol,
            ClassElement valueType
    ) {
        GeneratorContext classContext = GeneratorContext.create(problemReporter, valueName.toString());

        MethodSpec deserialize = MethodSpec.methodBuilder("deserialize")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(JsonParser.class, DECODER)
                .returns(valueName)
                .addException(IOException.class)
                .addCode(symbol.deserialize(classContext.newMethodContext(DECODER), valueType, expr -> CodeBlock.of("return $L;\n", expr)))
                .build();

        MethodSpec serialize = MethodSpec.methodBuilder("serialize")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(JsonGenerator.class, ENCODER)
                .addParameter(valueName, "value")
                .addException(IOException.class)
                .addCode(symbol.serialize(classContext.newMethodContext("value", ENCODER), valueType, CodeBlock.of("value")))
                .build();

        TypeSpec.Builder serializer = TypeSpec.classBuilder(serializerName.simpleName())
                .addAnnotation(Secondary.class)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(ParameterizedTypeName.get(ClassName.get(Serializer.class), valueName))
                .addMethod(serialize)
                .addMethod(deserialize);

        MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Inject.class);
        CodeBlock.Builder constructorCodeBuilder = CodeBlock.builder();
        classContext.getInjected().forEach((type, injected) -> {
            constructorBuilder.addParameter(type, injected.fieldName);
            constructorCodeBuilder.addStatement("this.$N = $N", injected.fieldName, injected.fieldName);
            serializer.addField(type, injected.fieldName, Modifier.PRIVATE, Modifier.FINAL);
        });
        constructorBuilder.addCode(constructorCodeBuilder.build());
        serializer.addMethod(constructorBuilder.build());

        JavaFile generatedFile = JavaFile.builder(serializerName.packageName(), serializer.build()).build();
        return new GenerationResult(serializerName, generatedFile);
    }

    public static final class GenerationResult {
        private final ClassName serializerClassName;
        private final JavaFile generatedFile;

        private GenerationResult(ClassName serializerClassName, JavaFile generatedFile) {
            this.serializerClassName = serializerClassName;
            this.generatedFile = generatedFile;
        }

        public ClassName getSerializerClassName() {
            return serializerClassName;
        }

        public JavaFile getGeneratedFile() {
            return generatedFile;
        }
    }
}
