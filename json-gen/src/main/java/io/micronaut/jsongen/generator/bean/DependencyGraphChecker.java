package io.micronaut.jsongen.generator.bean;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.jsongen.SerializableBean;
import io.micronaut.jsongen.generator.SerializerLinker;
import io.micronaut.jsongen.generator.SerializerSymbol;

import java.util.Map;
import java.util.Optional;

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
        @Nullable
        private final Node parent;
        private final ClassElement type;
        @Nullable
        private final Element debugElement;

        private boolean isStructureNode;

        Node(Node parent, ClassElement type, Element debugElement) {
            this.parent = parent;
            this.type = type;
            this.debugElement = debugElement;
        }

        private boolean checkParent() {
            Node node = parent;
            while (node != null) {
                if (node.isStructureNode && isSameType(node.type, this.type)) {
                    // found a cycle!
                    break;
                }
                node = node.parent;
            }
            if (node == null) {
                // no cycle
                return true;
            }
            // `node` has the same type as us, now.
            // walk up the path again, up to the parent with the cycle.
            StringBuilder pathBuilder = new StringBuilder(debugElement == null ? "*" : debugElement.getSimpleName());
            Node pathNode = this;
            while (pathNode != node) {
                pathNode = pathNode.parent;
                assert pathNode != null;
                String elementName = pathNode.debugElement == null ? "*" : pathNode.debugElement.getSimpleName();
                // prepend the node
                pathBuilder.insert(0, elementName + "->");
            }

            warningContext.fail("Circular dependency: " + pathBuilder, debugElement);
            anyFailures = true;
            return false;
        }

        @Override
        public boolean visitStructure() {
            isStructureNode = true;
            return checkParent();
        }

        @Override
        public void visitStructureElement(SerializerSymbol dependencySymbol, ClassElement dependencyType, @Nullable Element element) {
            visitChild(dependencySymbol, dependencyType, element);
        }

        @Override
        public void visitInjected(ClassElement dependencyType, boolean provider) {
            if (provider) {
                // we don't care about recursion if it goes through a provider
                return;
            }

            Optional<ClassElement> classDecl = warningContext.getClassElement(dependencyType.getName());
            if (!classDecl.isPresent()) {
                // just ignore the type, nothing we can do.
                return;
            }

            if (classDecl.get().isAnnotationPresent(SerializableBean.class)) {
                visitChild(new InlineBeanSerializerSymbol(linker), dependencyType, null);
            } // else, a custom serializer.
        }

        private void visitChild(SerializerSymbol childSymbol, ClassElement dependencyType, Element element) {
            Node childNode = new Node(this, dependencyType, element);
            childSymbol.visitDependencies(childNode, dependencyType);
        }
    }
}
