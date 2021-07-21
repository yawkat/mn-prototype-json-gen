package io.micronaut.jsongen;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

import java.io.IOException;

public interface Serializer<T> {
    /**
     * Deserialize from the given {@code decoder}.
     * <p>
     * The decoder {@link JsonParser#getCurrentToken()} should be positioned at the first token of this value.
     */
    T deserialize(JsonParser decoder) throws IOException;

    void serialize(JsonGenerator encoder, T value) throws IOException;
}
