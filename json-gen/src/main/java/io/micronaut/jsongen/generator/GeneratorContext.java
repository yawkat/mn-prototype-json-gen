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

import java.util.HashSet;
import java.util.Set;

public class GeneratorContext {
    private final Set<String> usedVariables = new HashSet<>();

    public GeneratorContext() {
    }

    /**
     * Register a local variable with a fixed name (e.g. a parameter)
     *
     * @param name The unique name of the local variable
     * @throws IllegalStateException if the variable is already in use
     */
    public void registerLocalVariable(String name) {
        if (!usedVariables.add(name)) {
            throw new IllegalStateException("Variable already in use: " + name);
        }
    }

    /**
     * Create a new unique variable, with a name similar to the given {@code nameHint}.
     *
     * @param nameHint a readable name for this variable. Not necessarily a valid java identifier, or unique
     * @return The unique generated variable name
     */
    public String newLocalVariable(String nameHint) {
        String sane = nameHint.replaceAll("[^a-zA-Z0-9]", "_");
        if (usedVariables.add(sane)) {
            return sane;
        }
        for (int i = 0; ; i++) {
            String withSuffix = sane + "$" + i;
            if (usedVariables.add(withSuffix)) {
                return withSuffix;
            }
        }
    }
}
