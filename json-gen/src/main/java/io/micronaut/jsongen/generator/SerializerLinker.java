package io.micronaut.jsongen.generator;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import io.micronaut.inject.ast.ClassElement;

public class SerializerLinker {
    private final InlineIterableSerializerSymbol.Array array = new InlineIterableSerializerSymbol.Array(this);
    private final InlineIterableSerializerSymbol.ArrayList arrayList = new InlineIterableSerializerSymbol.ArrayList(this);

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
        if (type.getName().equals("java.lang.Iterable") || type.getName().equals("java.util.List") || type.getName().equals("java.util.ArrayList")) {
            return arrayList;
        }
        if (type.isPrimitive()) {
            return PrimitiveSerializerSymbol.INSTANCE;
        }
        if (type.isAssignable(String.class)) {
            return StringSerializerSymbol.INSTANCE;
        }
        return new SerializerSymbol() {
            private final ClassName serializerClassName = ClassName.get(type.getPackageName(), type.getSimpleName() + "$Serializer");

            @Override
            public CodeBlock serialize(ClassElement type, CodeBlock readExpression) {
                return CodeBlock.of("$T.INSTANCE.serialize($N, " + readExpression + ");\n", serializerClassName, Names.ENCODER);
            }

            @Override
            public DeserializationCode deserialize(ClassElement type) {
                return new DeserializationCode(CodeBlock.of("$T.INSTANCE.deserialize($N)", Names.DECODER));
            }
        };
    }
}
