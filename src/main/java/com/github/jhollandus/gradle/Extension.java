package com.github.jhollandus.gradle;


import org.gradle.api.Project;

public class CommsExtension {
    private final Project project;

    public CommsExtension(Project project) {
        this.project = project;
    }

    public Project getProject() {
        return project;
    }
}
