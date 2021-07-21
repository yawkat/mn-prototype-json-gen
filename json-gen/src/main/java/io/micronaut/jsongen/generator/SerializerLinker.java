package io.micronaut.jsongen.generator;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.jsongen.generator.bean.InlineBeanSerializerSymbol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SerializerLinker {
    final InlineIterableSerializerSymbol.ArrayImpl array = new InlineIterableSerializerSymbol.ArrayImpl(this);
    final InlineIterableSerializerSymbol.ArrayListImpl arrayList = new InlineIterableSerializerSymbol.ArrayListImpl(this);
    final InlineBeanSerializerSymbol bean = new InlineBeanSerializerSymbol(this);

    private final List<SerializerSymbol> symbolList = new ArrayList<>(Arrays.asList(
            array,
            arrayList,
            PrimitiveSerializerSymbol.INSTANCE,
            StringSerializerSymbol.INSTANCE,
            bean
    ));

    public final SerializerSymbol findSymbolForSerialize(ClassElement type) {
        return findSymbolGeneric(type);
    }

    public final SerializerSymbol findSymbolForDeserialize(ClassElement type) {
        return findSymbolGeneric(type);
    }

    protected void registerSymbol(SerializerSymbol symbol) {
        symbolList.add(0, symbol);
    }

    protected SerializerSymbol findSymbolGeneric(ClassElement type) {
        for (SerializerSymbol serializerSymbol : symbolList) {
            if (serializerSymbol.canSerialize(type)) {
                return serializerSymbol;
            }
        }
        throw new UnsupportedOperationException("No symbol for " + type);
    }
}
