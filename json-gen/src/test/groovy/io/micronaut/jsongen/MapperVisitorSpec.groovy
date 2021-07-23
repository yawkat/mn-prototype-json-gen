package io.micronaut.jsongen


import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class MapperVisitorSpec extends AbstractTypeElementSpec implements SerializerUtils {
    void "generator creates a serializer for jackson annotations"() {
        given:
        def compiled = buildClassLoader('example.Test', '''
package example;

@io.micronaut.jsongen.SerializableBean
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

@io.micronaut.jsongen.SerializableBean
class A {
    B b;
    String bar;
}

@io.micronaut.jsongen.SerializableBean
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

        def serializerB = (Serializer<?>) compiled.loadClass('example.B$Serializer').getConstructor().newInstance()
        def serializerA = (Serializer<?>) compiled.loadClass('example.A$Serializer').getConstructor(Serializer.class).newInstance(serializerB)

        expect:
        serializeToString(serializerB, b) == '{"foo":"456"}'
        serializeToString(serializerA, a) == '{"b":{"foo":"456"},"bar":"123"}'
        deserializeFromString(serializerB, '{"foo":"456"}').foo == "456"
        deserializeFromString(serializerA, '{"b":{"foo":"456"},"bar":"123"}').bar == "123"
        deserializeFromString(serializerA, '{"b":{"foo":"456"},"bar":"123"}').b.foo == "456"
    }

    void "lists"() {
        given:
        def compiled = buildClassLoader('example.Test', '''
package example;

import java.util.List;

@io.micronaut.jsongen.SerializableBean
class Test {
    List<String> list;
}
''')

        def constructor = compiled.loadClass("example.Test").getDeclaredConstructor()
        constructor.accessible = true
        def test = constructor.newInstance()

        test.list = ['foo', 'bar']

        def serializer = (Serializer<?>) compiled.loadClass('example.Test$Serializer').getConstructor().newInstance()

        expect:
        serializeToString(serializer, test) == '{"list":["foo","bar"]}'
        deserializeFromString(serializer, '{"list":["foo","bar"]}').list == ['foo', 'bar']
    }
}
