package io.micronaut.jsongen.generator;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.squareup.javapoet.*;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.jsongen.Serializer;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.Map;

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
        return new GenerationResult(valueType, serializerName, generatedFile);
    }

    public static class GenerationResult implements SerializerSymbol {
        private final ClassElement supportedValueType;

        public final ClassName serializerClassName;
        public final JavaFile generatedFile;

        private GenerationResult(ClassElement supportedValueType, ClassName serializerClassName, JavaFile generatedFile) {
            this.supportedValueType = supportedValueType;
            this.serializerClassName = serializerClassName;
            this.generatedFile = generatedFile;
        }

        @Override
        public boolean canSerialize(ClassElement type) {
            return isSameType(type, supportedValueType);
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

    private static boolean isSameType(ClassElement a, ClassElement b) {
        if (!a.getName().equals(b.getName())) return false;
        Map<String, ClassElement> aArgs = a.getTypeArguments();
        Map<String, ClassElement> bArgs = b.getTypeArguments();
        if (!aArgs.keySet().equals(bArgs.keySet())) return false;
        for (String argument : aArgs.keySet()) {
            if (!isSameType(aArgs.get(argument), bArgs.get(argument))) {
                return false;
            }
        }
        return true;
    }
}
