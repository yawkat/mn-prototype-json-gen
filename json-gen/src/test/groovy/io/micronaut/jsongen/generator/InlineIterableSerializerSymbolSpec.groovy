package io.micronaut.jsongen.generator


import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.ClassElement
import org.spockframework.gentyref.TypeToken
import spock.lang.Ignore

class InlineIterableSerializerSymbolSpec extends AbstractSymbolSpec {
    def "array"() {
        given:
        def serializer = buildBasicSerializer(String[].class, new SerializerLinker(null).array)

        expect:
        deserializeFromString(serializer, '["foo", "bar"]') == new String[]{'foo', 'bar'}
        serializeToString(serializer, new String[]{'foo', 'bar'}) == '["foo","bar"]'
    }

    @Ignore("creating generic ClassElements doesn't work")
    def "list"() {
        given:
        def listType = new TypeToken<List<String>>() {}.getType()
        def listElement = ClassElement.of(List.class, AnnotationMetadata.EMPTY_METADATA, ['E': ClassElement.of(String.class)])
        def serializer = buildBasicSerializer(listType, new SerializerLinker(null).arrayList, listElement)

        expect:
        deserializeFromString(serializer, '["foo", "bar"]') == ['foo', 'bar']
        serializeToString(serializer, ['foo', 'bar']) == '["foo","bar"]'
    }
}
