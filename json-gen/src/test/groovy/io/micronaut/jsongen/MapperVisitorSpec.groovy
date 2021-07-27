package io.micronaut.jsongen

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.BeanProvider

import java.lang.reflect.ParameterizedType

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

    void "recursive with proper annotation"() {
        given:
        def compiled = buildClassLoader('example.Test', '''
package example;

@io.micronaut.jsongen.SerializableBean
class Test {
    @io.micronaut.jsongen.RecursiveSerialization Test foo;
}
''')

        def constructor = compiled.loadClass("example.Test").getDeclaredConstructor()
        constructor.accessible = true
        def test = constructor.newInstance()
        test.foo = constructor.newInstance()

        def provider = new BeanProvider() {
            @Override
            Object get() {
                return (Serializer<?>) compiled.loadClass('example.Test$Serializer').getConstructor(BeanProvider.class).newInstance(this)
            }
        }
        def serializer = provider.get()

        expect:
        // serializeToString(serializer, test) == '{"list":["foo","bar"]}' todo: null support
        deserializeFromString(serializer, '{"foo":{}}').foo.foo == null
    }

    void "simple recursive without proper annotation gives error"() {
        when:
        buildClassLoader('example.Test', '''
package example;

@io.micronaut.jsongen.SerializableBean
class Test {
    Test foo;
}
''')
        then:
        def e = thrown Exception

        expect:
        e.message.contains("Circular dependency")
        e.message.contains("foo")
    }

    void "list recursive without proper annotation gives error"() {
        when:
        buildClassLoader('example.Test', '''
package example;

@io.micronaut.jsongen.SerializableBean
class Test {
    Test[] foo;
}
''')
        then:
        def e = thrown Exception

        expect:
        e.message.contains("Circular dependency")
        e.message.contains("foo")
    }

    void "mutually recursive without proper annotation gives error"() {
        when:
        buildClassLoader('example.A', '''
package example;

@io.micronaut.jsongen.SerializableBean
class A {
    B b;
}
@io.micronaut.jsongen.SerializableBean
class B {
    A a;
}
''')
        then:
        def e = thrown Exception

        expect:
        e.message.contains("Circular dependency")
        e.message.contains("A->b->*->a->*")
    }

    void "recursive ref to type with dedicated serializer doesn't error"() {
        // todo: this is sensible behavior since the user may decide to supply her own Serializer<B>, but is it intuitive?
        when:
        buildClassLoader('example.A', '''
package example;

@io.micronaut.jsongen.SerializableBean
class A {
    B b;
}
// not annotated
class B {
    A a;
}
''')
        then:
        return
    }

    void "nested generic"() {
        given:
        def compiled = buildClassLoader('example.Test', '''
package example;

@io.micronaut.jsongen.SerializableBean
class A {
    B<C> b;
}

@io.micronaut.jsongen.SerializableBean
class B<T> {
    T foo;
}

@io.micronaut.jsongen.SerializableBean
class C {
    String bar;
}
''')

        def constructorA = compiled.loadClass("example.A").getDeclaredConstructor()
        constructorA.accessible = true
        def a = constructorA.newInstance()

        def constructorB = compiled.loadClass("example.B").getDeclaredConstructor()
        constructorB.accessible = true
        def b = constructorB.newInstance()

        def constructorC = compiled.loadClass("example.C").getDeclaredConstructor()
        constructorC.accessible = true
        def c = constructorC.newInstance()

        a.b = b
        b.foo = c
        c.bar = "123"

        def serializerC = (Serializer<?>) compiled.loadClass('example.C$Serializer').getConstructor().newInstance()
        def serializerBClass = compiled.loadClass('example.B$Serializer')
        def serializerB = (Serializer<?>) serializerBClass.getConstructor(Serializer.class).newInstance(serializerC)
        def serializerA = (Serializer<?>) compiled.loadClass('example.A$Serializer').getConstructor(Serializer.class).newInstance(serializerB)

        def genericSerializerParam = serializerBClass.getDeclaredConstructor(Serializer.class).getGenericParameterTypes()[0]

        expect:
        serializeToString(serializerA, a) == '{"b":{"foo":{"bar":"123"}}}'
        deserializeFromString(serializerA, '{"b":{"foo":{"bar":"123"}}}').b.foo.bar == "123"

        genericSerializerParam instanceof ParameterizedType
        // todo: ideally, the Serializer<B> would be generic and accept a Serializer<T>. Otherwise, the @Inject will fail
        // ((ParameterizedType) genericSerializerParam).actualTypeArguments[0] instanceof TypeVariable
    }
}
