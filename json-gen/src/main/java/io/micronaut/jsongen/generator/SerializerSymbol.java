package io.micronaut.jsongen.generator;

import com.squareup.javapoet.CodeBlock;
import io.micronaut.inject.ast.ClassElement;

public interface SerializerSymbol {
    boolean canSerialize(ClassElement type);

    /**
     * Generate code that writes the value returned by {@code readExpression} into {@link Names#ENCODER}.
     *
     * @param readExpression The expression that reads the value. Must only be evaluated once.
     */
    CodeBlock serialize(GeneratorContext generatorContext, ClassElement type, CodeBlock readExpression);

    /**
     * Generate code that reads a value from {@link Names#DECODER}.
     * <p>
     * Decoder should be positioned at the first token of the value (as specified by
     * {@link io.micronaut.jsongen.Serializer#deserialize})
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
}
