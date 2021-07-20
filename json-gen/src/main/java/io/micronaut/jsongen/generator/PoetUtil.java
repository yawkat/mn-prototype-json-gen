package io.micronaut.jsongen.generator;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PrimitiveElement;

import java.util.Map;

public class PoetUtil {
    private PoetUtil() {}

    public static TypeName toTypeName(ClassElement clazz) {
        if (clazz.isArray()) {
            return ArrayTypeName.of(toTypeName(clazz.fromArray()));
        }
        if (clazz.isPrimitive()) {
            if (clazz.equals(PrimitiveElement.BYTE)) return TypeName.BYTE;
            else if (clazz.equals(PrimitiveElement.SHORT)) return TypeName.SHORT;
            else if (clazz.equals(PrimitiveElement.CHAR)) return TypeName.CHAR;
            else if (clazz.equals(PrimitiveElement.INT)) return TypeName.INT;
            else if (clazz.equals(PrimitiveElement.LONG)) return TypeName.LONG;
            else if (clazz.equals(PrimitiveElement.FLOAT)) return TypeName.FLOAT;
            else if (clazz.equals(PrimitiveElement.DOUBLE)) return TypeName.DOUBLE;
            else if (clazz.equals(PrimitiveElement.BOOLEAN)) return TypeName.BOOLEAN;
            else if (clazz.equals(PrimitiveElement.VOID)) return TypeName.VOID;
            else throw new AssertionError("unknown primitive type " + clazz);
        }
        ClassName className = ClassName.get(clazz.getPackageName(), clazz.getSimpleName()); // TODO: nested types
        Map<String, ClassElement> typeArguments = clazz.getTypeArguments();
        if (typeArguments.isEmpty()) {
            return className;
        } else {
            // TODO
            throw new UnsupportedOperationException();
        }
    }
}
