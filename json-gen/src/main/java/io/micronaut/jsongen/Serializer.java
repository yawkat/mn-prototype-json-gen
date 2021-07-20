package io.micronaut.jsongen;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

import java.io.IOException;

public interface Serializer<T> {
    T deserialize(JsonParser decoder) throws IOException;

    void serialize(JsonGenerator encoder, T value) throws IOException;
}
