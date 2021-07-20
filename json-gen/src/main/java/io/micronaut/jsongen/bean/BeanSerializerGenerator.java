package io.micronaut.jsongen.bean;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.squareup.javapoet.*;
import io.micronaut.annotation.processing.visitor.JavaClassElement;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.jsongen.Serializer;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BeanSerializerGenerator {
    private static final String ENCODER = "encoder";
    private static final String DECODER = "decoder";
    private static final String VALUE = "value";
    private static final String INSTANCE = "INSTANCE";

    private final ClassElement clazz;
    private final BeanDefinition definition;

    /**
     * Map of sanitized property names. <br>
     * Key: property name ({@link BeanDefinition.Property#name})
     * Value: a valid java identifier
     */
    private final Map<String, String> sanitizedPropertyNames;

    public BeanSerializerGenerator(ClassElement clazz) {
        this.clazz = clazz;
        this.definition = BeanIntrospector.introspect(clazz);

        sanitizedPropertyNames = new HashMap<>();
        for (BeanDefinition.Property prop : definition.props.values()) {
            // prefix with $ so we get no collision with other variables we use in normal control flow
            String sane = "$" + prop.name.replaceAll("[^a-z0-9]", "_");
            // avoid collisions
            while (sanitizedPropertyNames.containsValue(sane)) {
                sane += "_";
            }
            sanitizedPropertyNames.put(prop.name, sane);
        }
    }

    public JavaFile generate() {
        String name = clazz.getSimpleName() + "$Serializer";
        ClassName fqcn = ClassName.get(clazz.getPackageName(), name);
        TypeSpec serializer = TypeSpec.classBuilder(name)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(ParameterizedTypeName.get(ClassName.get(Serializer.class), toTypeName(clazz)))
                .addField(FieldSpec.builder(fqcn, INSTANCE)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer("new $T()", fqcn)
                        .build())
                .addMethod(MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PRIVATE)
                        .build())
                .addMethod(MethodSpec.methodBuilder("serialize")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(JsonGenerator.class, ENCODER)
                        .addParameter(toTypeName(clazz), VALUE)
                        .addException(IOException.class)
                        .addCode(generateSerialize())
                        .build())
                .addMethod(MethodSpec.methodBuilder("deserialize")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(JsonParser.class, DECODER)
                        .returns(toTypeName(clazz)) // todo: specialize for generics?
                        .addException(IOException.class)
                        .addCode(generateDeserialize())
                        .build())
                .build();
        return JavaFile.builder(clazz.getPackageName(), serializer).build();
    }

    private CodeBlock generateSerialize() {
        CodeBlock.Builder serialize = CodeBlock.builder();
        // passing the value to writeStartObject helps with debugging, but will not affect functionality
        serialize.addStatement(ENCODER + ".writeStartObject(" + VALUE + ")");
        for (BeanDefinition.Property prop : definition.props.values()) {
            serialize.addStatement(ENCODER + ".writeFieldName($S)", prop.name);
            serialize.addStatement(serializeBeanProperty(prop));
        }
        serialize.addStatement(ENCODER + ".writeEndObject()");
        return serialize.build();
    }

    private CodeBlock serializeBeanProperty(BeanDefinition.Property property) {
        ClassElement type;
        String readExpression;
        if (property.getter != null) {
            type = property.getter.getGenericReturnType();
            readExpression = VALUE + "." + property.getter.getName() + "()";
        } else if (property.isGetter != null) {
            type = property.isGetter.getGenericReturnType();
            readExpression = VALUE + "." + property.isGetter.getName() + "()";
        } else if (property.field != null) {
            type = property.field.getGenericType();
            readExpression = VALUE + "." + property.field.getName();
        } else {
            throw new UnsupportedOperationException(); // TODO
        }
        return serializeValue(type, readExpression);
    }

    private CodeBlock serializeValue(ClassElement type, String readExpression) {
        if (type.isArray()) {
            return serializeIterableLike(readExpression, type.fromArray());
        }
        if (type.isAssignable(Iterable.class)) {
            return serializeIterableLike(readExpression, type.getTypeArguments(Iterable.class).get("T"));
        }
        // todo: maps, special primitive arrays, bytes...
        if (type.isPrimitive()) {
            // type.isArray is checked above
            if (type.equals(PrimitiveElement.BOOLEAN)) {
                return CodeBlock.of(ENCODER + ".writeBoolean(" + readExpression + ");\n");
            } else {
                return CodeBlock.of(ENCODER + ".writeNumber(" + readExpression + ");\n");
            }
        }
        if (type.isAssignable(String.class)) {
            return CodeBlock.of(ENCODER + ".writeString(" + readExpression + ");\n");
        }
        return CodeBlock.of("$T.$N.serialize(" + readExpression + ")", ClassName.get(type.getPackageName(), type.getSimpleName() + "$Serializer"), INSTANCE);
    }

    private CodeBlock serializeIterableLike(String readExpression, ClassElement elementType) {
        return CodeBlock.builder()
                .beginControlFlow("for ($T item : " + readExpression + ")", toTypeName(elementType))
                .add(serializeValue(elementType, "item"))
                .endControlFlow()
                .build();
    }

    private CodeBlock generateDeserialize() {
        CodeBlock.Builder deserialize = CodeBlock.builder();
        deserialize.add("if ($N.nextToken() != $T.START_OBJECT) throw new $T();\n", DECODER, JsonToken.class, IOException.class); // todo: error msg

        // types used for deserialization
        Map<BeanDefinition.Property, ClassElement> deserializeTypes = definition.props.values().stream().collect(Collectors.toMap(prop -> prop, prop -> {
            if (prop.setter != null) {
                return prop.setter.getParameters()[0].getGenericType(); // TODO: bounds checks
            } else if (prop.field != null) {
                return prop.field.getGenericType();
            } else {
                throw new UnsupportedOperationException(); // TODO: we can still generate serializer code
            }
        }));
        // create a local variable for each property
        for (BeanDefinition.Property prop : definition.props.values()) {
            deserialize.addStatement("$T $N = " + getDefaultValueExpression(deserializeTypes.get(prop)), toTypeName(deserializeTypes.get(prop)), sanitizedPropertyNames.get(prop.name));
        }

        // main parse loop
        deserialize.beginControlFlow("while (true)");
        deserialize.addStatement("$T token = $N.nextToken()", JsonToken.class, DECODER);
        deserialize.add("if (token == $T.END_OBJECT) break;\n", JsonToken.class);
        deserialize.add("if (token != $T.FIELD_NAME) throw new $T();\n", JsonToken.class, IOException.class); // todo: error msg
        deserialize.beginControlFlow("switch ($N.getCurrentName())", DECODER);
        for (BeanDefinition.Property prop : definition.props.values()) {
            deserialize.beginControlFlow("case $S:", prop.name);
            // TODO: check for duplicate field
            deserialize.addStatement("$N = " + deserializeValue(deserialize, deserializeTypes.get(prop)), sanitizedPropertyNames.get(prop.name));
            deserialize.addStatement("break");
            deserialize.endControlFlow();
        }
        deserialize.endControlFlow();
        deserialize.endControlFlow();

        // todo: check for missing fields

        // assemble the result object

        // todo: @JsonCreator
        if (definition.defaultConstructor instanceof ConstructorElement) {
            deserialize.addStatement("$T result = new $T()", toTypeName(clazz), toTypeName(clazz));
        } else if (definition.defaultConstructor.isStatic()) {
            // TODO edge cases?
            deserialize.addStatement("$T result = $T.$N()", toTypeName(clazz), toTypeName(definition.defaultConstructor.getDeclaringType()), definition.defaultConstructor.getName());
        } else {
            throw new UnsupportedOperationException("Creator must be static method or constructor");
        }
        for (BeanDefinition.Property prop : definition.props.values()) {
            String localVariable = sanitizedPropertyNames.get(prop.name);
            if (prop.setter != null) {
                deserialize.addStatement("result.$N($N)", prop.setter.getName(), localVariable);
            } else if (prop.field != null) {
                deserialize.addStatement("result.$N = $N", prop.field.getName(), localVariable);
            } else {
                // TODO: fail gracefully, can still serialize
                throw new UnsupportedOperationException("Cannot set property " + prop.name);
            }
        }
        deserialize.addStatement("return result");

        return deserialize.build();
    }

    /**
     * @return The expression string that gives the final value. Must only be evaluated once, immediately after the code generated by this method.
     */
    private String deserializeValue(CodeBlock.Builder currentBlock, ClassElement type) {
        if (type.isArray()) {
            ClassElement elementType = type.fromArray();
            currentBlock.addStatement("$T<$T> list = new $T<>()", List.class, toTypeName(elementType), ArrayList.class);
            currentBlock.add(deserializeCollection("list", elementType));
            currentBlock.addStatement("$T array = list.toArray(new $T[0])", type, type.fromArray());
            return "array";
        }
        if (type.isAssignable(Iterable.class)) {
            // TODO: find proper collection implementation
            throw new UnsupportedOperationException();
        }
        // todo: maps, special primitive arrays, bytes...
        if (type.isPrimitive()) {
            // type.isArray is checked above
            if (type.equals(PrimitiveElement.BOOLEAN)) {
                return DECODER + ".nextBooleanValue()";
            } else if (type.equals(PrimitiveElement.BYTE)) {
                return DECODER + ".nextByteValue()";
            } else if (type.equals(PrimitiveElement.SHORT)) {
                return DECODER + ".nextByteValue()";
            } else if (type.equals(PrimitiveElement.CHAR)) {
                return "(char) " + DECODER + ".nextIntValue()"; // TODO
            } else if (type.equals(PrimitiveElement.INT)) {
                return DECODER + ".nextIntValue()";
            } else if (type.equals(PrimitiveElement.LONG)) {
                return DECODER + ".nextLongValue()";
            } else if (type.equals(PrimitiveElement.FLOAT)) {
                return DECODER + ".nextFloatValue()";
            } else if (type.equals(PrimitiveElement.DOUBLE)) {
                return DECODER + ".nextDoubleValue()";
            } else {
                throw new AssertionError("unknown primitive type " + type);
            }
        }
        if (type.getName().equals("java.lang.String") || type.getName().equals("java.lang.CharSequence")) {
            return DECODER + ".nextTextValue()";
        }
        currentBlock.addStatement(
                "$T value = $T.$N.deserialize(" + DECODER + ")",
                toTypeName(type),
                INSTANCE,
                ClassName.get(type.getPackageName(), type.getSimpleName() + "$Serializer") // todo: nested types
        );
        return "value";
    }

    private CodeBlock deserializeCollection(String collectionVariable, ClassElement elementType) {
        // TODO
        throw new UnsupportedOperationException();
    }

    private static String getDefaultValueExpression(ClassElement clazz) {
        if (clazz.isPrimitive() && !clazz.isArray()) {
            if (clazz.equals(PrimitiveElement.VOID)) {
                throw new UnsupportedOperationException("void cannot be assigned");
            } else if (clazz.equals(PrimitiveElement.BOOLEAN)) {
                return "false";
            } else {
                return "0";
            }
        } else {
            return "null";
        }
    }

    private static TypeName toTypeName(ClassElement clazz) {
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
