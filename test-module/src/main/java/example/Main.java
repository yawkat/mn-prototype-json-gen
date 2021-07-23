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
package example;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import io.micronaut.context.ApplicationContext;
import io.micronaut.jsongen.Serializer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;

@Singleton
public final class Main {
    private final Serializer<Image> listSerializer;

    @Inject
    public Main(Serializer<Image> listSerializer) {
        this.listSerializer = listSerializer;
    }

    public static void main(String[] args) throws IOException {
        Image image = new Image();
        image.setId(123);
        image.setUri("https://imgur.com/op8V7KE");
        image.setTags(new ArrayList<>());
        Tag popcorn = new Tag();
        popcorn.setValue("popcorn");
        image.getTags().add(popcorn);
        Tag gif = new Tag();
        gif.setValue("gif");
        image.getTags().add(gif);

        StringWriter stringWriter = new StringWriter();
        try (ApplicationContext ctx = ApplicationContext.run();
             JsonGenerator gen = new JsonFactory().createGenerator(stringWriter)) {
            ctx.getBean(Main.class).listSerializer.serialize(gen, image);
        }
        System.out.println(stringWriter);
    }
}
