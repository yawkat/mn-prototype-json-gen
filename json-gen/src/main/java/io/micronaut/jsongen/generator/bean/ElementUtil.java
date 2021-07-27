package io.micronaut.jsongen.generator.bean;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;

import java.util.Map;

final class ElementUtil {
    private ElementUtil() {
    }

    static boolean equals(ClassElement a, ClassElement b) {
        // todo: mn3 .equals
        if (!a.getName().equals(b.getName())) {
            return false;
        }
        Map<String, ClassElement> aArgs = a.getTypeArguments();
        Map<String, ClassElement> bArgs = b.getTypeArguments();
        if (!aArgs.keySet().equals(bArgs.keySet())) {
            return false;
        }
        for (String argument : aArgs.keySet()) {
            if (!equals(aArgs.get(argument), bArgs.get(argument))) {
                return false;
            }
        }
        return true;
    }

    static boolean equals(MethodElement a, MethodElement b) {
        if (!equals(a.getDeclaringType(), b.getDeclaringType())) {
            return false;
        }
        if (!a.getName().equals(b.getName())) {
            return false;
        }
        if (!equals(a.getGenericReturnType(), b.getGenericReturnType())) {
            return false;
        }
        ParameterElement[] paramsA = a.getParameters();
        ParameterElement[] paramsB = b.getParameters();
        if (paramsA.length != paramsB.length) {
            return false;
        }
        for (int i = 0; i < paramsA.length; i++) {
            if (!equals(paramsA[i].getGenericType(), paramsB[i].getGenericType())) {
                return false;
            }
        }
        return true;
    }
}
