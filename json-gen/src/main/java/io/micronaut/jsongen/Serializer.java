/*
 * Copyright 2017-2021 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.jsongen;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

import java.io.IOException;

public interface Serializer<T> {
    /**
     * Deserialize from the given {@code decoder}.
     * <p>
     * The decoder {@link JsonParser#currentToken()} should be positioned at the first token of this value.
     *
     * @param decoder The decoder to parse from
     * @return The decoded value
     */
    T deserialize(JsonParser decoder) throws IOException;

    void serialize(JsonGenerator encoder, T value) throws IOException;
}
