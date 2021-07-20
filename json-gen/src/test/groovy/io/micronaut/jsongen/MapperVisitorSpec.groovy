package io.micronaut.jsongen

import com.fasterxml.jackson.core.JsonFactory
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class MapperVisitorSpec extends AbstractTypeElementSpec {
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
}
