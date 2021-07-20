package io.micronaut.jsongen.generator

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeSpec
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.jsongen.Serializer
import io.micronaut.jsongen.SerializerUtils

import javax.lang.model.element.Modifier
import java.lang.reflect.Type

class AbstractSymbolSpec extends AbstractTypeElementSpec implements SerializerUtils {
    public <T> Serializer<T> buildBasicSerializer(Type type, SerializerSymbol symbol, ClassElement classElement = ClassElement.of(type)) {
        def deserCode = symbol.deserialize(classElement)
        def javaFile = JavaFile.builder("example", TypeSpec.classBuilder("Test")
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(ParameterizedTypeName.get(Serializer.class, type))
                .addMethod(MethodSpec.methodBuilder("serialize")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(JsonGenerator.class, Names.ENCODER)
                        .addParameter(type, "value")
                        .addException(IOException.class)
                        .addCode(symbol.serialize(classElement, CodeBlock.of("value")))
                        .build())
                .addMethod(MethodSpec.methodBuilder("deserialize")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(JsonParser.class, Names.DECODER)
                        .addException(IOException.class)
                        .returns(type)
                        .addCode(CodeBlock.builder()
                                .addStatement('$N.nextToken()', Names.DECODER)
                                .add(deserCode.statements)
                                .addStatement("return " + deserCode.resultExpression)
                                .build())
                        .build())
                .build()).build()
        return (Serializer<T>) buildClassLoader("example.Test", javaFile.toString()).loadClass("example.Test").getConstructor().newInstance()
    }
}
