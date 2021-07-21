package io.micronaut.jsongen

import com.fasterxml.jackson.core.JsonFactory
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

import java.lang.reflect.Constructor

class MapperVisitorSpec extends AbstractTypeElementSpec implements SerializerUtils {
    void "generator creates a serializer for jackson annotations"() {
        given:
        def compiled = buildClassLoader('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties
public class Test {
}
''')

        def serializerClass = compiled.loadClass('example.Test$Serializer')

        expect:
        serializerClass != null
        Serializer.class.isAssignableFrom(serializerClass)
    }

    void "nested beans"() {
        given:
        def compiled = buildClassLoader('example.Test', '''
package example;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties
class A {
    B b;
    String bar;
}

@JsonIgnoreProperties
class B {
    String foo;
}
''')


        def constructorA = compiled.loadClass("example.A").getDeclaredConstructor()
        constructorA.accessible = true
        def a = constructorA.newInstance()

        def constructorB = compiled.loadClass("example.B").getDeclaredConstructor()
        constructorB.accessible = true
        def b = constructorB.newInstance()

        a.b = b
        a.bar = "123"
        b.foo = "456"

        def serializerA = (Serializer<?>) compiled.loadClass('example.A$Serializer').getField("INSTANCE").get(null)
        def serializerB = (Serializer<?>) compiled.loadClass('example.B$Serializer').getField("INSTANCE").get(null)

        expect:
        serializeToString(serializerB, b) == '{"foo":"456"}'
        serializeToString(serializerA, a) == '{"b":{"foo":"456"},"bar":"123"}'
        deserializeFromString(serializerB, '{"foo":"456"}').foo == "456"
        deserializeFromString(serializerA, '{"b":{"foo":"456"},"bar":"123"}').bar == "123"
        deserializeFromString(serializerA, '{"b":{"foo":"456"},"bar":"123"}').b.foo == "456"
    }
}
