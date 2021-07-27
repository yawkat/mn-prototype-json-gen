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
package io.micronaut.jsongen;

import io.micronaut.annotation.processing.visitor.JavaVisitorContext;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.jsongen.generator.ProblemReporter;
import io.micronaut.jsongen.generator.SerializerLinker;
import io.micronaut.jsongen.generator.SingletonSerializerGenerator;
import io.micronaut.jsongen.generator.bean.DependencyGraphChecker;
import io.micronaut.jsongen.generator.bean.InlineBeanSerializerSymbol;

import javax.annotation.processing.Filer;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;

public class MapperVisitor implements TypeElementVisitor<SerializableBean, SerializableBean> {
    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        SerializerLinker linker = new SerializerLinker(context);
        InlineBeanSerializerSymbol inlineBeanSerializer = linker.inlineBean;
        if (!inlineBeanSerializer.canSerializeStandalone(element)) {
            return;
        }
        DependencyGraphChecker depChecker = new DependencyGraphChecker(context, linker);
        depChecker.checkCircularDependencies(inlineBeanSerializer, element, element);
        if (depChecker.hasAnyFailures()) {
            return;
        }
        ProblemReporter problemReporter = new ProblemReporter();
        SingletonSerializerGenerator.GenerationResult generationResult = SingletonSerializerGenerator.generate(problemReporter, element, inlineBeanSerializer);

        problemReporter.reportTo(context);
        if (problemReporter.isFailed()) {
            return;
        }

        // todo: gen serviceloader
        // todo: support groovy/kt
        Filer filer = ((JavaVisitorContext) context).getProcessingEnv().getFiler();
        try {
            JavaFileObject sourceFile = filer.createSourceFile(generationResult.getSerializerClassName().reflectionName());
            try (Writer writer = sourceFile.openWriter()) {
                generationResult.getGeneratedFile().writeTo(writer);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    @NonNull
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
