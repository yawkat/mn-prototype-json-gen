package io.micronaut.jsongen.generator.bean;

import com.fasterxml.jackson.core.JsonToken;
import com.squareup.javapoet.CodeBlock;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.jsongen.JsonParseException;
import io.micronaut.jsongen.generator.GeneratorContext;
import io.micronaut.jsongen.generator.PoetUtil;
import io.micronaut.jsongen.generator.SerializerLinker;
import io.micronaut.jsongen.generator.SerializerSymbol;

import java.util.Map;
import java.util.stream.Collectors;

import static io.micronaut.jsongen.generator.Names.DECODER;
import static io.micronaut.jsongen.generator.Names.ENCODER;

public class InlineBeanSerializerSymbol implements SerializerSymbol {
    private final SerializerLinker linker;

    public InlineBeanSerializerSymbol(SerializerLinker linker) {
        this.linker = linker;
    }

    @Override
    public boolean canSerialize(ClassElement type) {
        return true;
    }

    @Override
    public CodeBlock serialize(GeneratorContext generatorContext, ClassElement type, CodeBlock readExpression) {
        BeanDefinition definition = BeanIntrospector.introspect(type, true);

        String objectVarName = generatorContext.newLocalVariable("object");

        CodeBlock.Builder serialize = CodeBlock.builder();
        serialize.addStatement("$T $N = $L", PoetUtil.toTypeName(type), objectVarName, readExpression);
        // passing the value to writeStartObject helps with debugging, but will not affect functionality
        serialize.addStatement("$N.writeStartObject($N)", ENCODER, objectVarName);
        for (BeanDefinition.Property prop : definition.props) {
            serialize.addStatement("$N.writeFieldName($S)", ENCODER, prop.name);
            ClassElement propType;
            CodeBlock propRead;
            if (prop.getter != null) {
                propType = prop.getter.getGenericReturnType();
                propRead = CodeBlock.of("$N.$N()", objectVarName, prop.getter.getName());
            } else if (prop.field != null) {
                propType = prop.field.getGenericType();
                propRead = CodeBlock.of("$N.$N", objectVarName, prop.field.getName());
            } else {
                throw new UnsupportedOperationException(); // TODO
            }
            serialize.add(linker.findSymbolForSerialize(propType).serialize(generatorContext, propType, propRead));
        }
        serialize.addStatement("$N.writeEndObject()", ENCODER);
        return serialize.build();
    }

    @Override
    public DeserializationCode deserialize(GeneratorContext generatorContext, ClassElement type) {
        BeanDefinition definition = BeanIntrospector.introspect(type, false);

        CodeBlock.Builder deserialize = CodeBlock.builder();
        deserialize.add("if ($N.getCurrentToken() != $T.START_OBJECT) throw $T.from($N, \"Unexpected token \" + $N.getCurrentToken() + \", expected START_OBJECT\");\n",
                DECODER, JsonToken.class, JsonParseException.class, DECODER, DECODER);

        // types used for deserialization
        Map<BeanDefinition.Property, ClassElement> deserializeTypes = definition.props.stream().collect(Collectors.toMap(prop -> prop, prop -> {
            if (prop.setter != null) {
                return prop.setter.getParameters()[0].getGenericType(); // TODO: bounds checks
            } else if (prop.field != null) {
                return prop.field.getGenericType();
            } else {
                throw new UnsupportedOperationException(); // TODO: we can still generate serializer code
            }
        }));
        Map<BeanDefinition.Property, String> localVariableNames = definition.props.stream()
                .collect(Collectors.toMap(prop -> prop, prop -> generatorContext.newLocalVariable(prop.name)));

        // create a local variable for each property
        for (BeanDefinition.Property prop : definition.props) {
            deserialize.addStatement("$T $N = $L", PoetUtil.toTypeName(deserializeTypes.get(prop)), localVariableNames.get(prop), getDefaultValueExpression(deserializeTypes.get(prop)));
        }

        // main parse loop
        deserialize.beginControlFlow("while (true)");
        String tokenVariable = generatorContext.newLocalVariable("token");
        deserialize.addStatement("$T $N = $N.nextToken()", JsonToken.class, tokenVariable, DECODER);
        deserialize.add("if ($N == $T.END_OBJECT) break;\n", tokenVariable, JsonToken.class);
        deserialize.add("if ($N != $T.FIELD_NAME) throw $T.from($N, \"Unexpected token \" + token + \", expected END_OBJECT or FIELD_NAME\");\n",
                tokenVariable, JsonToken.class, JsonParseException.class, DECODER);
        String fieldNameVariable = generatorContext.newLocalVariable("fieldName");
        deserialize.addStatement("$T $N = $N.getCurrentName()", String.class, fieldNameVariable, DECODER);
        deserialize.addStatement("$N.nextToken()", DECODER);
        deserialize.beginControlFlow("switch ($N)", fieldNameVariable);
        for (BeanDefinition.Property prop : definition.props) {
            deserialize.beginControlFlow("case $S:", prop.name);
            // TODO: check for duplicate field
            ClassElement propType = deserializeTypes.get(prop);
            SerializerSymbol.DeserializationCode deserializationCode = linker.findSymbolForDeserialize(propType).deserialize(generatorContext, propType);
            deserialize.add(deserializationCode.getStatements());
            deserialize.addStatement("$N = $L", localVariableNames.get(prop), deserializationCode.getResultExpression());
            deserialize.addStatement("break");
            deserialize.endControlFlow();
        }
        deserialize.endControlFlow();
        deserialize.endControlFlow();

        // todo: check for missing fields
        // todo: check for unknown fields

        // assemble the result object

        // todo: @JsonCreator
        String resultVariable = generatorContext.newLocalVariable("result");
        if (definition.defaultConstructor instanceof ConstructorElement) {
            deserialize.addStatement("$T $N = new $T()", PoetUtil.toTypeName(type), resultVariable, PoetUtil.toTypeName(type));
        } else if (definition.defaultConstructor.isStatic()) {
            // TODO edge cases?
            deserialize.addStatement("$T $N = $T.$N()", PoetUtil.toTypeName(type), resultVariable, PoetUtil.toTypeName(definition.defaultConstructor.getDeclaringType()), definition.defaultConstructor.getName());
        } else {
            throw new UnsupportedOperationException("Creator must be static method or constructor");
        }
        for (BeanDefinition.Property prop : definition.props) {
            String localVariable = localVariableNames.get(prop);
            if (prop.setter != null) {
                deserialize.addStatement("$N.$N($N)", resultVariable, prop.setter.getName(), localVariable);
            } else if (prop.field != null) {
                deserialize.addStatement("$N.$N = $N", resultVariable, prop.field.getName(), localVariable);
            } else {
                // TODO: fail gracefully, can still serialize
                throw new UnsupportedOperationException("Cannot set property " + prop.name);
            }
        }
        return new DeserializationCode(
                deserialize.build(),
                CodeBlock.of("$N", resultVariable)
        );
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

}
