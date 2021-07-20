package io.micronaut.jsongen.generator

import com.fasterxml.jackson.core.JsonFactory

class StringSerializerSymbolSpec extends AbstractSymbolSpec {
    def "string"() {
        given:
        def serializer = buildBasicSerializer(String.class, StringSerializerSymbol.INSTANCE)

        expect:
        serializer.deserialize(new JsonFactory().createParser('"foo"')) == 'foo'
        serializeToString(serializer, 'foo') == '"foo"'
    }
}
