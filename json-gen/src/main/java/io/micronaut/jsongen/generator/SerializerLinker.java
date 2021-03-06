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
package io.micronaut.jsongen.generator;

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.jsongen.generator.bean.InlineBeanSerializerSymbol;

import java.util.Arrays;
import java.util.List;

public final class SerializerLinker {
    @Internal
    public final InlineBeanSerializerSymbol inlineBean;

    final InlineIterableSerializerSymbol.ArrayImpl array = new InlineIterableSerializerSymbol.ArrayImpl(this);
    final InlineIterableSerializerSymbol.ArrayListImpl arrayList = new InlineIterableSerializerSymbol.ArrayListImpl(this);

    private final List<SerializerSymbol> symbolList;

    public SerializerLinker(VisitorContext typeResolutionContext) {
        inlineBean = new InlineBeanSerializerSymbol(this, typeResolutionContext);
        symbolList = Arrays.asList(
                array,
                arrayList,
                PrimitiveSerializerSymbol.INSTANCE,
                StringSerializerSymbol.INSTANCE,
                InlineEnumSerializerSymbol.INSTANCE,
                // for serializing beans inline (@SerializableBean(inline=true))
                inlineBean,
                new InjectingSerializerSymbol(this)
        );
    }

    public SerializerSymbol findSymbol(ClassElement type) {
        for (SerializerSymbol serializerSymbol : symbolList) {
            if (serializerSymbol.canSerialize(type)) {
                return serializerSymbol;
            }
        }
        throw new UnsupportedOperationException("No symbol for " + type);
    }
}
