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

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;

public final class JsonParseException extends JacksonException {
    private final JsonParser parser;
    private final JsonLocation location;

    private JsonParseException(String msg, Throwable rootCause, JsonParser parser, JsonLocation location) {
        super(msg, rootCause);
        this.parser = parser;
        this.location = location;
    }

    public static JsonParseException from(JsonParser parser, String msg) {
        return new JsonParseException(msg, null, parser, parser.getCurrentLocation());
    }

    @Override
    public JsonLocation getLocation() {
        return location;
    }

    @Override
    public String getOriginalMessage() {
        return super.getMessage();
    }

    @Override
    public Object getProcessor() {
        return parser;
    }

    @Override
    public String getMessage() {
        // similar to jackson JsonProcessingException
        String msg = super.getMessage();
        if (msg == null) {
            msg = "N/A";
        }
        if (location != null) {
            msg = msg + "\n at " + location;
        }
        return msg;
    }
}
