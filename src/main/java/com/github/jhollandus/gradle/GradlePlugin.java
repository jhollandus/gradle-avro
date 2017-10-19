package com.github.jhollandus.gradle;

import com.github.jhollandus.gradle.avro.CommsAvroPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.BasePluginConvention;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.language.java.plugins.JavaLanguagePlugin;

import java.io.File;

public class CommsGradlePlugin implements Plugin<Project> {
    public static File getDistDir(Project project) {
        BasePluginConvention baseConv = project.getConvention().findByType(BasePluginConvention.class);
        if(baseConv != null) {
            return baseConv.getDistsDir();
        } else {
            return new File(project.getBuildDir(), "distributions");
        }
    }

    public static File getGeneratedDir(File buildDir, String name) {
        return new File(buildDir, String.format("generated-src/%s", name));
    }

    @Override
    public void apply(Project project) {
        project.getExtensions().create("comms", CommsExtension.class, project);
        project.getPluginManager().apply(JavaLanguagePlugin.class);
        project.getPluginManager().apply(CommsAvroPlugin.class);
    }
}
