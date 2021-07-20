package io.micronaut.jsongen

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonFactoryBuilder

trait SerializerUtils {
    static final JsonFactory JSON_FACTORY = new JsonFactoryBuilder().build();

    static <T> String serializeToString(Serializer<T> serializer, T value) {
        def writer = new StringWriter()
        def generator = JSON_FACTORY.createGenerator(writer)
        serializer.serialize(generator, value)
        generator.close()
        return writer.toString()
    }
}