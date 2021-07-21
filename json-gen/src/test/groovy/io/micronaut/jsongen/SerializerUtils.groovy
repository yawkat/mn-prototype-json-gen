package io.micronaut.jsongen

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonFactoryBuilder
import com.fasterxml.jackson.core.JsonParser
import org.intellij.lang.annotations.Language

trait SerializerUtils {
    static final JsonFactory JSON_FACTORY = new JsonFactoryBuilder().build();

    @Language("json")
    static <T> String serializeToString(Serializer<T> serializer, T value) {
        def writer = new StringWriter()
        def generator = JSON_FACTORY.createGenerator(writer)
        serializer.serialize(generator, value)
        generator.close()
        return writer.toString()
    }

    static <T> T deserializeFromString(Serializer<T> serializer, @Language("json") String json) {
        def parser = JSON_FACTORY.createParser(json)
        parser.nextToken() // place parser at first token
        return serializer.deserialize(parser)
    }
}