package io.micronaut.jsongen.generator;

import java.util.HashSet;
import java.util.Set;

public class GeneratorContext {
    private final Set<String> usedVariables = new HashSet<>();

    public GeneratorContext() {}

    /**
     * Register a local variable with a fixed name (e.g. a parameter)
     *
     * @throws IllegalStateException if the variable is already in use
     */
    public void registerLocalVariable(String name) {
        if (!usedVariables.add(name)) {
            throw new IllegalStateException("Variable already in use: " + name);
        }
    }

    /**
     * Create a new unique variable, with a name similar to the given {@code nameHint}
     *
     * @param nameHint a readable name for this variable. Not necessarily a valid java identifier, or unique
     */
    public String newLocalVariable(String nameHint) {
        String sane = nameHint.replaceAll("[^a-zA-Z0-9]", "_");
        if (usedVariables.add(sane)) {
            return sane;
        }
        for (int i = 0;; i++) {
            String withSuffix = sane + "$" + i;
            if (usedVariables.add(withSuffix)) {
                return withSuffix;
            }
        }
    }
}
