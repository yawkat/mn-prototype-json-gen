package io.micronaut.jsongen.generator


import io.micronaut.jsongen.JsonParseException

class StringSerializerSymbolSpec extends AbstractSymbolSpec {
    def "string"() {
        given:
        def serializer = buildBasicSerializer(String.class, StringSerializerSymbol.INSTANCE)

        expect:
        deserializeFromString(serializer, '"foo"') == 'foo'
        serializeToString(serializer, 'foo') == '"foo"'
    }

    def "wrong token throws error"() {
        given:
        def serializer = buildBasicSerializer(String.class, StringSerializerSymbol.INSTANCE)
        when:
        deserializeFromString(serializer, '52')
        then:
        thrown JsonParseException
    }
}
