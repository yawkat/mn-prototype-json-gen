package io.micronaut.jsongen.generator

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.TypeName
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.jsongen.Serializer
import io.micronaut.jsongen.SerializerUtils

import java.lang.reflect.Type

class AbstractSymbolSpec extends AbstractTypeElementSpec implements SerializerUtils {
    public <T> Serializer<T> buildBasicSerializer(Type type, SerializerSymbol symbol, ClassElement classElement = ClassElement.of(type)) {
        def problemReporter = new ProblemReporter()
        def generationResult = SingletonSerializerGenerator.generate(
                problemReporter,
                ClassName.get("example", "SerializerImpl"),
                TypeName.get(type),
                symbol,
                classElement
        )
        if (problemReporter.isFailed()) {
            throw new AssertionError("Problems found, use debugger to tell which :)");
        }

        def loader = buildClassLoader(generationResult.serializerClassName.reflectionName(), generationResult.generatedFile.toString())
        def serializerClass = loader.loadClass(generationResult.serializerClassName.reflectionName())
        return (Serializer<T>) serializerClass.getConstructor().newInstance()
    }
}
