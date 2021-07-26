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

import com.squareup.javapoet.CodeBlock;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;

public interface SerializerSymbol {
    boolean canSerialize(ClassElement type);

    /**
     * @return a symbol equivalent to this one, but with the capability of dealing with recursive / circular serialization issues.
     */
    default SerializerSymbol withRecursiveSerialization() {
        return this;
    }

    void visitDependencies(DependencyVisitor visitor, ClassElement type);

    /**
     * Generate code that writes the value returned by {@code readExpression} into {@link Names#ENCODER}.
     *
     * @param generatorContext The context of the generator, e.g. declared local variables.
     * @param type The type of the value being serialized.
     * @param readExpression The expression that reads the value. Must only be evaluated once.
     * @return The code block containing statements that perform the serialization.
     */
    CodeBlock serialize(GeneratorContext generatorContext, ClassElement type, CodeBlock readExpression);

    /**
     * Generate code that reads a value from {@link Names#DECODER}.
     * <p>
     * Decoder should be positioned at the first token of the value (as specified by
     * {@link io.micronaut.jsongen.Serializer#deserialize})
     *
     * @param generatorContext The context of the generator, e.g. declared local variables.
     * @param type The type of the value being deserialized.
     * @return The code that performs the deserialization.
     */
    DeserializationCode deserialize(GeneratorContext generatorContext, ClassElement type);

    class DeserializationCode {
        /**
         * The main deserialization code.
         */
        private final CodeBlock statements;
        /**
         * The expression used to access the final deserialized value. Must be evaluated immediately after the other
         * {@link #statements}, in the same scope. Must only be evaluated once.
         */
        private final CodeBlock resultExpression;

        public DeserializationCode(CodeBlock statements, CodeBlock resultExpression) {
            this.statements = statements;
            this.resultExpression = resultExpression;
        }

        public DeserializationCode(CodeBlock resultExpression) {
            this(CodeBlock.of(""), resultExpression);
        }

        public CodeBlock getStatements() {
            return statements;
        }

        public CodeBlock getResultExpression() {
            return resultExpression;
        }
    }

    interface DependencyVisitor {
        /**
         * @return Whether to visit the elements of this structure
         */
        boolean visitStructure();

        void visitStructureElement(SerializerSymbol dependencySymbol, ClassElement dependencyType, @Nullable Element element);

        void visitInjected(ClassElement dependencyType, boolean provider);
    }
}
