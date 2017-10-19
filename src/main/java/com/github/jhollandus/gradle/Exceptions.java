package com.github.jhollandus.gradle;

import org.gradle.api.GradleException;

public interface Exceptions {

    @FunctionalInterface
    interface UncheckCreate<T> {
        T create() throws Exception;
    }

    @FunctionalInterface
    interface UncheckRun {
       void run() throws Exception;
    }

    static <T> T asGradleException(UncheckCreate<T> factory) {
        try {
            return factory.create();
        } catch (Exception t) {
            throw convertThrowable(t);
        }
    }

    static void asGradleException(UncheckRun runner) {
        asGradleException(() -> {
            runner.run();
            return null;
        });
    }

    static RuntimeException convertThrowable(Exception e) {
        if(e instanceof GradleException) {
            return (GradleException) e;
        } else {
            throw new GradleException(e.getMessage(), e);
        }
    }
}
