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
import com.squareup.javapoet.TypeName;

import java.util.*;

public final class GeneratorContext {
    /**
     * A readable path to this context, used for better error messages.
     */
    private final String readablePath;

    private final IdentifierScope fields;
    private final IdentifierScope localVariables;

    private final Map<TypeName, Injected> injected;

    private GeneratorContext(
            String readablePath,
            IdentifierScope fields,
            IdentifierScope localVariables,
            Map<TypeName, Injected> injected) {
        this.readablePath = readablePath;
        this.fields = fields;
        this.localVariables = localVariables;
        this.injected = injected;
    }

    static GeneratorContext create(String rootReadablePath) {
        return new GeneratorContext(rootReadablePath, new IdentifierScope(), null, new HashMap<>());
    }

    public String getReadablePath() {
        return readablePath;
    }

    public GeneratorContext withSubPath(String element) {
        // the other variables are mutable, so we can just reuse them
        return new GeneratorContext(readablePath + "->" + element, fields, localVariables, injected);
    }

    public GeneratorContext newMethodContext(String... usedLocals) {
        if (this.localVariables != null) {
            throw new IllegalStateException("Nesting of local variable scopes not supported");
        }
        return new GeneratorContext(readablePath, fields, new IdentifierScope(usedLocals), injected);
    }

    /**
     * Create a new unique variable, with a name similar to the given {@code nameHint}.
     *
     * @param nameHint a readable name for this variable. Not necessarily a valid java identifier, or unique
     * @return The unique generated variable name
     */
    public String newLocalVariable(String nameHint) {
        return localVariables.create(nameHint);
    }

    public Injected requestInjection(TypeName type) {
        return injected.computeIfAbsent(type, t -> {
            String fieldName = fields.create(t.toString());
            return new Injected(fieldName);
        });
    }

    public Map<TypeName, Injected> getInjected() {
        return injected;
    }

    public static final class Injected {
        final String fieldName;

        private final CodeBlock accessExpression;

        private Injected(String fieldName) {
            this.fieldName = fieldName;
            this.accessExpression = CodeBlock.of("this.$N", fieldName);
        }

        public CodeBlock getAccessExpression() {
            return accessExpression;
        }
    }

    private static class IdentifierScope {
        private final Set<String> identifiers;

        public IdentifierScope(String... predefined) {
            identifiers = new HashSet<>(Arrays.asList(predefined));
        }

        String create(String nameHint) {
            // todo: handle reserved identifiers
            String sane = nameHint.replaceAll("[^a-zA-Z0-9]", "_");
            if (identifiers.add(sane)) {
                return sane;
            }
            for (int i = 0; ; i++) {
                String withSuffix = sane + "$" + i;
                if (identifiers.add(withSuffix)) {
                    return withSuffix;
                }
            }
        }
    }
}
