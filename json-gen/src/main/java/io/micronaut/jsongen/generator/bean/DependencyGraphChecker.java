package io.micronaut.jsongen.generator.bean;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.jsongen.SerializableBean;
import io.micronaut.jsongen.generator.SerializerLinker;
import io.micronaut.jsongen.generator.SerializerSymbol;

import java.util.Map;

public class DependencyGraphChecker {
    private final VisitorContext warningContext;
    private final SerializerLinker linker;

    private boolean anyFailures = false;

    public DependencyGraphChecker(VisitorContext warningContext, SerializerLinker linker) {
        this.warningContext = warningContext;
        this.linker = linker;
    }

    public void checkCircularDependencies(SerializerSymbol symbol, ClassElement type, Element rootElement) {
        symbol.visitDependencies(new Node(null, type, rootElement), type);
    }

    public boolean hasAnyFailures() {
        return anyFailures;
    }

    private static boolean isSameType(ClassElement a, ClassElement b) {
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
            if (!isSameType(aArgs.get(argument), bArgs.get(argument))) {
                return false;
            }
        }
        return true;
    }

    private class Node implements SerializerSymbol.DependencyVisitor {
        /*
         * This logic is a bit intricate. When bean properties are visited, visitInline is called. When an inject
         * *symbol* is visited, visitInject is called.
         *
         * We cannot fail on every property that has an ancestor of the same type, because it might still be fine if the
         * symbol for the property goes through a provider. So what we do instead is create a new node for the property,
         * and only check for the circular dependency when a member of that property is visited. If that member is
         * visitInject, we can still ignore the circular dependency.
         */

        private final Node parent;
        private final ClassElement type;
        private final Element debugElement;

        Node(Node parent, ClassElement type, Element debugElement) {
            this.parent = parent;
            this.type = type;
            this.debugElement = debugElement;
        }

        private boolean hasAncestorType(ClassElement type) {
            return parent != null && (isSameType(parent.type, type) || parent.hasAncestorType(type));
        }

        private boolean checkParent() {
            if (hasAncestorType(type)) {
                // todo: better message
                warningContext.fail("Circular dependency", debugElement);
                anyFailures = true;
                return false;
            } else {
                return true;
            }
        }

        @Override
        public void visitInline(SerializerSymbol dependencySymbol, ClassElement dependencyType, @Nullable Element element) {
            if (!checkParent()) {
                return;
            }

            visitChild(dependencySymbol, dependencyType, element);
        }

        @Override
        public void visitInjected(ClassElement dependencyType, boolean provider) {
            if (provider) {
                // we don't care about recursion if it goes through a provider
                return;
            }
            if (!checkParent()) {
                return;
            }

            if (dependencyType.isAnnotationPresent(SerializableBean.class)) {
                visitChild(new InlineBeanSerializerSymbol(linker), dependencyType, null);
            } // else, a custom serializer.
        }

        private void visitChild(SerializerSymbol childSymbol, ClassElement dependencyType, Element element) {
            Node childNode = new Node(this, dependencyType, element == null ? debugElement : element);
            childSymbol.visitDependencies(childNode, dependencyType);
        }
    }
}
