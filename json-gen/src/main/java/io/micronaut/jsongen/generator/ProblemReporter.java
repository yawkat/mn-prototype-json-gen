package io.micronaut.jsongen.generator;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.ArrayList;
import java.util.List;

public final class ProblemReporter {
    private final List<Problem> problems = new ArrayList<>();
    private boolean failed = false;

    public void fail(String message, @Nullable Element element) {
        problems.add(new Problem(Level.FAIL, message, element));
        failed = true;
    }

    public void warn(String message, @Nullable Element element) {
        problems.add(new Problem(Level.WARN, message, element));
    }

    public void info(String message, @Nullable Element element) {
        problems.add(new Problem(Level.INFO, message, element));
    }

    public void reportTo(VisitorContext context) {
        for (Problem problem : problems) {
            switch (problem.level) {
                case INFO:
                    context.info(problem.message, problem.element);
                    break;
                case WARN:
                    context.warn(problem.message, problem.element);
                    break;
                case FAIL:
                    context.fail(problem.message, problem.element);
                    break;
            }
        }
    }

    public boolean isFailed() {
        return failed;
    }

    private static final class Problem {
        final Level level;
        final String message;
        final Element element;

        Problem(Level level, String message, Element element) {
            this.message = message;
            this.element = element;
            this.level = level;
        }
    }

    private enum Level {
        INFO, WARN, FAIL
    }
}
