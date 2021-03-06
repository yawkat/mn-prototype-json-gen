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

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PrimitiveElement;

import java.util.Map;

public final class PoetUtil {
    private PoetUtil() {
    }

    public static TypeName toTypeName(ClassElement clazz) {
        if (clazz.isArray()) {
            return ArrayTypeName.of(toTypeName(clazz.fromArray()));
        }
        if (clazz.isPrimitive()) {
            if (clazz.equals(PrimitiveElement.BYTE)) {
                return TypeName.BYTE;
            } else if (clazz.equals(PrimitiveElement.SHORT)) {
                return TypeName.SHORT;
            } else if (clazz.equals(PrimitiveElement.CHAR)) {
                return TypeName.CHAR;
            } else if (clazz.equals(PrimitiveElement.INT)) {
                return TypeName.INT;
            } else if (clazz.equals(PrimitiveElement.LONG)) {
                return TypeName.LONG;
            } else if (clazz.equals(PrimitiveElement.FLOAT)) {
                return TypeName.FLOAT;
            } else if (clazz.equals(PrimitiveElement.DOUBLE)) {
                return TypeName.DOUBLE;
            } else if (clazz.equals(PrimitiveElement.BOOLEAN)) {
                return TypeName.BOOLEAN;
            } else if (clazz.equals(PrimitiveElement.VOID)) {
                return TypeName.VOID;
            } else {
                throw new AssertionError("unknown primitive type " + clazz);
            }
        }
        ClassName className = ClassName.get(clazz.getPackageName(), clazz.getSimpleName()); // TODO: nested types
        Map<String, ClassElement> typeArguments = clazz.getTypeArguments();
        if (typeArguments.isEmpty()) {
            if (clazz.getName().equals("<any>")) {
                // todo: investigate further. Seems to happen when the input source has unresolvable types
                throw new IllegalArgumentException("Type resolution error?");
            }
            return className;
        } else {
            // we assume the typeArguments Map is ordered by source declaration
            return ParameterizedTypeName.get(
                    className,
                    typeArguments.values().stream().map(PoetUtil::toTypeName).toArray(TypeName[]::new));
        }
    }
}
