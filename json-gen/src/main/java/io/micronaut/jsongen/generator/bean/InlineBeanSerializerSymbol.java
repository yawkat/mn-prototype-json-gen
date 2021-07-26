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

import java.util.*;
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
        // this symbol is not part of the normal linker chain, so this does not apply.
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitDependencies(DependencyVisitor visitor, ClassElement type) {
        if (!visitor.visitStructure()) {
            return;
        }
        // have to check both ser/deser, in case property types differ (e.g. when setters and getters have different types)
        // technically, this could lead to false positives for checking, since ser types will be considered in a subgraph that is only reachable through deser
        for (boolean ser : new boolean[] {true, false}) {
            for (BeanDefinition.Property prop : BeanIntrospector.introspect(type, ser).props) {
                SerializerSymbol symbol = linker.findSymbol(prop.getType());
                if (prop.permitRecursiveSerialization) {
                    symbol = symbol.withRecursiveSerialization();
                }
                visitor.visitStructureElement(symbol, prop.getType(), prop.getElement());
            }
        }
    }

    private SerializerSymbol findSymbol(BeanDefinition.Property prop) {
        SerializerSymbol symbol = linker.findSymbol(prop.getType());
        if (prop.permitRecursiveSerialization) {
            symbol = symbol.withRecursiveSerialization();
        }
        return symbol;
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
            CodeBlock propRead;
            if (prop.getter != null) {
                propRead = CodeBlock.of("$N.$N()", objectVarName, prop.getter.getName());
            } else if (prop.field != null) {
                propRead = CodeBlock.of("$N.$N", objectVarName, prop.field.getName());
            } else {
                throw new AssertionError("No accessor, property should have been filtered");
            }
            serialize.add(findSymbol(prop).serialize(generatorContext.withSubPath(prop.name), prop.getType(), propRead));
        }
        serialize.addStatement("$N.writeEndObject()", ENCODER);
        return serialize.build();
    }

    @Override
    public DeserializationCode deserialize(GeneratorContext generatorContext, ClassElement type) {
        return new DeserGen(generatorContext, type).generate();
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

    private class DeserGen {
        private final GeneratorContext generatorContext;
        private final ClassElement type;

        private final BeanDefinition definition;
        /**
         * Names of the local variables properties are saved in.
         */
        private final Map<BeanDefinition.Property, String> localVariableNames;

        private final DuplicatePropertyManager duplicatePropertyManager;

        /**
         * Main deser code.
         */
        private final CodeBlock.Builder deserialize = CodeBlock.builder();

        DeserGen(GeneratorContext generatorContext, ClassElement type) {
            this.generatorContext = generatorContext;
            this.type = type;

            definition = BeanIntrospector.introspect(type, false);
            localVariableNames = definition.props.stream()
                    .collect(Collectors.toMap(prop -> prop, prop -> generatorContext.newLocalVariable(prop.name)));
            duplicatePropertyManager = new DuplicatePropertyManager(generatorContext, definition.props);
        }

        private DeserializationCode generate() {
            deserialize.add("if ($N.getCurrentToken() != $T.START_OBJECT) throw $T.from($N, \"Unexpected token \" + $N.getCurrentToken() + \", expected START_OBJECT\");\n",
                    DECODER, JsonToken.class, JsonParseException.class, DECODER, DECODER);

            duplicatePropertyManager.emitMaskDeclarations(deserialize);

            // create a local variable for each property
            for (BeanDefinition.Property prop : definition.props) {
                deserialize.addStatement("$T $N = $L", PoetUtil.toTypeName(prop.getType()), localVariableNames.get(prop), getDefaultValueExpression(prop.getType()));
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
                deserializeProperty(prop);
                deserialize.addStatement("break");
                deserialize.endControlFlow();
            }
            deserialize.endControlFlow();
            deserialize.endControlFlow();

            duplicatePropertyManager.emitCheckRequired(deserialize);

            // todo: check for unknown fields

            // assemble the result object

            String resultVariable = combineLocalsToResultVariable();
            return new DeserializationCode(
                    deserialize.build(),
                    CodeBlock.of("$N", resultVariable)
            );
        }

        private void deserializeProperty(BeanDefinition.Property prop) {
            duplicatePropertyManager.emitReadVariable(deserialize, prop);

            SerializerSymbol.DeserializationCode deserializationCode = findSymbol(prop)
                    .deserialize(generatorContext.withSubPath(prop.name), prop.getType());
            deserialize.add(deserializationCode.getStatements());
            deserialize.addStatement("$N = $L", localVariableNames.get(prop), deserializationCode.getResultExpression());
        }

        /**
         * Combine all the local variables into the final result variable.
         *
         * @return the result variable name
         */
        private String combineLocalsToResultVariable() {
            String resultVariable = generatorContext.newLocalVariable("result");

            CodeBlock.Builder creatorParameters = CodeBlock.builder();
            boolean firstParameter = true;
            for (BeanDefinition.Property prop : definition.creatorProps) {
                if (!firstParameter) {
                    creatorParameters.add(", ");
                }
                creatorParameters.add("$L", localVariableNames.get(prop));
                firstParameter = false;
            }

            if (definition.creator instanceof ConstructorElement) {
                deserialize.addStatement("$T $N = new $T($L)", PoetUtil.toTypeName(type), resultVariable, PoetUtil.toTypeName(type), creatorParameters.build());
            } else if (definition.creator.isStatic()) {
                // TODO edge cases?
                deserialize.addStatement(
                        "$T $N = $T.$N($L)",
                        PoetUtil.toTypeName(type),
                        resultVariable,
                        PoetUtil.toTypeName(definition.creator.getDeclaringType()),
                        definition.creator.getName(),
                        creatorParameters.build()
                );
            } else {
                throw new AssertionError("bad creator, should have been detected in BeanIntrospector");
            }
            for (BeanDefinition.Property prop : definition.props) {
                String localVariable = localVariableNames.get(prop);
                if (prop.setter != null) {
                    deserialize.addStatement("$N.$N($N)", resultVariable, prop.setter.getName(), localVariable);
                } else if (prop.field != null) {
                    deserialize.addStatement("$N.$N = $N", resultVariable, prop.field.getName(), localVariable);
                } else {
                    if (prop.creatorParameter == null) {
                        throw new AssertionError("Cannot set property, should have been filtered out during introspection");
                    }
                }
            }
            return resultVariable;
        }
    }

    /**
     * This class detects duplicate and missing properties using an inlined BitSet.
     * <p>
     * Note: implementation must use the same bit layout as BitSet to allow internal use of {@link BitSet#toLongArray()}.
     */
    private static class DuplicatePropertyManager {
        private final List<BeanDefinition.Property> properties;

        private final List<String> maskVariables;
        private final Map<BeanDefinition.Property, Integer> offsets;

        private final BitSet requiredMask;

        DuplicatePropertyManager(
                GeneratorContext context,
                List<BeanDefinition.Property> properties
        ) {
            requiredMask = new BitSet(properties.size());
            this.properties = properties;

            offsets = new HashMap<>();
            int offset = 0;
            for (BeanDefinition.Property property : properties) {
                offsets.put(property, offset);
                // todo: only require when required=true is set
                if (property.creatorParameter != null) {
                    requiredMask.set(offset);
                }

                offset++;
            }

            // generate one mask for every 64 variables
            maskVariables = new ArrayList<>();
            for (int i = 0; i < offset; i += 64) {
                maskVariables.add(context.newLocalVariable("mask"));
            }
        }

        void emitMaskDeclarations(CodeBlock.Builder output) {
            for (String maskVariable : maskVariables) {
                output.addStatement("long $N = 0", maskVariable);
            }
        }

        void emitReadVariable(CodeBlock.Builder output, BeanDefinition.Property prop) {
            int offset = offsets.get(prop);
            String maskVariable = maskVariable(offset);
            String mask = mask(offset);
            output.add(
                    "if (($N & $L) != 0) throw $T.from($N, $S);\n",
                    maskVariable,
                    mask,
                    JsonParseException.class,
                    DECODER,
                    "Duplicate property " + prop.name
            );
            output.addStatement("$N |= $L", maskVariable, mask);
        }

        private String maskVariable(int offset) {
            return maskVariables.get(offset / 64);
        }

        private String mask(int offset) {
            // shift does an implicit modulo
            long value = 1L << offset;
            return toHexLiteral(value);
        }

        private String toHexLiteral(long value) {
            return "0x" + Long.toHexString(value) + "L";
        }

        void emitCheckRequired(CodeBlock.Builder output) {
            if (requiredMask.isEmpty()) {
                return;
            }

            // first, check whether there are any values missing, by simple mask comparison. This is the fast check.
            long[] expected = requiredMask.toLongArray();
            output.add("if (");
            boolean first = true;
            for (int i = 0; i < expected.length; i++) {
                long value = expected[i];
                if (value != 0) {
                    if (!first) {
                        output.add(" || ");
                    }
                    first = false;
                    String valueLiteral = toHexLiteral(value);
                    output.add("($N & $L) != $L", maskVariables.get(i), valueLiteral, valueLiteral);
                }
            }
            output.add(") {\n").indent();

            // if there are missing variables, determine which ones
            int offset = 0;
            for (BeanDefinition.Property prop : properties) {
                if (requiredMask.get(offset)) {
                    output.add(
                            "if (($N & $L) == 0) throw $T.from($N, $S);\n",
                            maskVariable(offset),
                            mask(offset),
                            JsonParseException.class,
                            DECODER,
                            "Missing property " + prop.name
                    );
                }
                offset++;
            }
            output.add("// should never reach here, all possible missing properties are checked\n");
            output.addStatement("throw new $T()", AssertionError.class);

            output.endControlFlow();
        }
    }
}
