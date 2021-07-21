package io.micronaut.jsongen.generator;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.squareup.javapoet.*;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.jsongen.Serializer;

import javax.lang.model.element.Modifier;
import java.io.IOException;

import static io.micronaut.jsongen.generator.Names.DECODER;
import static io.micronaut.jsongen.generator.Names.ENCODER;

public class SingletonSerializerGenerator {
    static final String INSTANCE_FIELD_NAME = "INSTANCE";

    private SingletonSerializerGenerator() {}

    public static GenerationResult generate(ClassElement clazz, SerializerSymbol symbol) {
        return generate(
                ClassName.get(clazz.getPackageName(), clazz.getSimpleName() + "$Serializer"),
                PoetUtil.toTypeName(clazz),
                symbol,
                clazz
        );
    }

    /**
     * @param serializerName FQCN of the generated serializer class
     * @param valueName type name to use for the value being serialized, must be a reference type
     * @param symbol symbol to use for serialization
     * @param valueType type to pass to the symbol for code generation. Usually identical to {@code valueName}, except for primitives
     */
    static GenerationResult generate(
            ClassName serializerName,
            TypeName valueName,
            SerializerSymbol symbol,
            ClassElement valueType
    ) {
        GeneratorContext deserContext = new GeneratorContext();
        deserContext.registerLocalVariable(DECODER);
        SerializerSymbol.DeserializationCode deserializationCode = symbol.deserialize(deserContext, valueType);
        MethodSpec deserialize = MethodSpec.methodBuilder("deserialize")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(JsonParser.class, DECODER)
                .returns(valueName)
                .addException(IOException.class)
                .addCode(CodeBlock.builder()
                        .add(deserializationCode.getStatements())
                        .addStatement("return $L", deserializationCode.getResultExpression())
                        .build())
                .build();

        GeneratorContext serContext = new GeneratorContext();
        serContext.registerLocalVariable("value");
        serContext.registerLocalVariable(ENCODER);
        MethodSpec serialize = MethodSpec.methodBuilder("serialize")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(JsonGenerator.class, ENCODER)
                .addParameter(valueName, "value")
                .addException(IOException.class)
                .addCode(symbol.serialize(serContext, valueType, CodeBlock.of("value")))
                .build();

        TypeSpec serializer = TypeSpec.classBuilder(serializerName.simpleName())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(ParameterizedTypeName.get(ClassName.get(Serializer.class), valueName))
                .addField(FieldSpec.builder(serializerName, INSTANCE_FIELD_NAME)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer("new $T()", serializerName)
                        .build())
                .addMethod(MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PRIVATE)
                        .build())
                .addMethod(serialize)
                .addMethod(deserialize)
                .build();
        JavaFile generatedFile = JavaFile.builder(serializerName.packageName(), serializer).build();
        return new GenerationResult(serializerName, generatedFile);
    }

    public static class GenerationResult implements SerializerSymbol {
        public final ClassName serializerClassName;
        public final JavaFile generatedFile;

        private GenerationResult(ClassName serializerClassName, JavaFile generatedFile) {
            this.serializerClassName = serializerClassName;
            this.generatedFile = generatedFile;
        }

        @Override
        public CodeBlock serialize(GeneratorContext generatorContext, ClassElement type, CodeBlock readExpression) {
            return CodeBlock.of("$T.$N.serialize($N, $L);\n", serializerClassName, INSTANCE_FIELD_NAME, Names.ENCODER, readExpression);
        }

        @Override
        public DeserializationCode deserialize(GeneratorContext generatorContext, ClassElement type) {
            return new DeserializationCode(CodeBlock.of("$T.$N.deserialize($N)", serializerClassName, INSTANCE_FIELD_NAME, Names.DECODER));
        }
    }
}
