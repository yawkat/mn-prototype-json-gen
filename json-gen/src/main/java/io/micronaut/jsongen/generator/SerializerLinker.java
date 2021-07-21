package io.micronaut.jsongen.generator;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.jsongen.generator.bean.InlineBeanSerializerSymbol;

public class SerializerLinker {
    final InlineIterableSerializerSymbol.ArrayImpl array = new InlineIterableSerializerSymbol.ArrayImpl(this);
    final InlineIterableSerializerSymbol.ArrayListImpl arrayList = new InlineIterableSerializerSymbol.ArrayListImpl(this);
    final InlineBeanSerializerSymbol bean = new InlineBeanSerializerSymbol(this);

    public SerializerSymbol findSymbolForSerialize(ClassElement type) {
        return findSymbolGeneric(type);
    }

    public SerializerSymbol findSymbolForDeserialize(ClassElement type) {
        return findSymbolGeneric(type);
    }

    private SerializerSymbol findSymbolGeneric(ClassElement type) {
        if (type.isArray()) {
            return array;
        }
        // todo: can this be prettier?
        if (type.getName().equals("java.lang.Iterable") ||
                type.getName().equals("java.util.Collection") ||
                type.getName().equals("java.util.List") ||
                type.getName().equals("java.util.ArrayList")) {
            return arrayList;
        }
        if (type.isPrimitive()) {
            return PrimitiveSerializerSymbol.INSTANCE;
        }
        if (type.isAssignable(String.class)) {
            return StringSerializerSymbol.INSTANCE;
        }
        if (type.getName().equals("java.lang.Object")) {
            // todo: this exists for fail-fast debugging for now, maybe we can fill Object fields with Maps/Lists at some point
            throw new IllegalArgumentException("Cannot deserialize Object");
        }
        // todo: reuse already-generated Serializers
        return bean;
    }
}
