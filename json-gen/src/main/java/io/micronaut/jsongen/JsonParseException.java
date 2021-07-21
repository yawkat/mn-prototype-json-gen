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
        if (msg == null) msg = "N/A";
        if (location != null) msg = msg + "\n at " + location;
        return msg;
    }
}
