package com.github.jhollandus.gradle.avro;

import com.commercehub.gradle.plugin.avro.GenerateAvroJavaTask;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.github.jhollandus.gradle.CommsExtension;
import com.github.jhollandus.gradle.CommsGradlePlugin;
import com.github.jhollandus.gradle.avro.model.CommsAvroModel;
import com.github.jhollandus.gradle.avro.task.AvroIdlToSchemata;
import com.github.jhollandus.gradle.avro.task.AvroSchemaValidate;
import org.apache.avro.compiler.specific.SpecificCompiler;
import org.apache.avro.generic.GenericData;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.model.Defaults;
import org.gradle.model.Model;
import org.gradle.model.ModelMap;
import org.gradle.model.Mutate;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;
import org.gradle.plugins.ide.idea.GenerateIdeaModule;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.IdeaModule;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class CommsAvroPlugin implements Plugin<Project> {
    private static final Logger logger = Logging.getLogger(CommsAvroPlugin.class);

    @Override
    public void apply(Project project) {
    }

    public static class Rules extends RuleSource {

        @Model
        void avro(CommsAvroModel model) {
        }

        @Defaults
        void setDefaults(CommsAvroModel model, @Path("buildDir") File buildDir) {

            model.getBindings().setStringType(GenericData.StringType.String);
            model.getBindings().setCreateSetters(false);
            model.getBindings().setEnabled(true);
            model.getBindings().setFieldVisibility(SpecificCompiler.FieldVisibility.PRIVATE);
            model.getBindings().setOutputCharacterEncoding(StandardCharsets.UTF_8.name());

            model.getValidation().setEnabled(true);
            model.getValidation().setCompareAll(true);
            model.getValidation().setCompatibility(AvroSchemaValidate.Compatibility.BACKWARDS);

            model.getIdl().getSource().srcDir(String.format("src/%s/idl", model.getName()))
                    .include("**/*." + AvroIdlToSchemata.AVRO_IDL_EXTENSION)
                    .include("**/*." + AvroIdlToSchemata.IDL_FRAGMENT_EXTENSION);

            model.getSchema().getSource().srcDir(String.format("src/%s/schema", model.getName()))
                    .include("**/*." + AvroSchemaValidate.AVRO_SCHEMA_EXTENSION);

            File generatedSrcDir = generatedAvroSrcDir(buildDir);
            model.getGeneratedJava().getSource().setSrcDirs(Collections.singleton(generatedSrcDir));
        }

        @Mutate
        void addTasks(ModelMap<Task> tasks, CommsAvroModel model, ExtensionContainer extensions) {
            Project project = extensions.getByType(CommsExtension.class).getProject();

            //create Idl task
            tasks.create("transformAvroIdl", AvroIdlToSchemata.class, task -> {
                logger.error("transformAvroIdl source: {}", model.getIdl().getSource().getSrcDirs());
                task.source(model.getIdl().getSource());
                task.setDest(model.getSchema().getSource().getSrcDirs().iterator().next());
            });

            tasks.create("validateAvroSchema", AvroSchemaValidate.class, task -> {
                task.source(model.getSchema().getSource());
                task.setCompatibility(model.getValidation().getCompatibility().name());
                task.setCompareAll(model.getValidation().getCompareAll());
                task.setEnabled(model.getValidation().getEnabled());
                task.dependsOn("transformAvroIdl");
            });

            tasks.create("zipAvroSchema", Zip.class, task -> {
                task.from(model.getSchema().getSource());
                task.include("**/*." + AvroSchemaValidate.AVRO_SCHEMA_EXTENSION);
                task.dependsOn("validateAvroSchema");
                task.setDestinationDir(CommsGradlePlugin.getDistDir(project));
                task.setBaseName(project.getName());
                task.setGroup(project.getGroup().toString());
                task.setVersion(project.getVersion().toString());

                PublishingExtension pubExt = extensions.findByType(PublishingExtension.class);
                if (pubExt != null) {
                    pubExt.getPublications().create(model.getSchema().getName(), MavenPublication.class, mvnPub -> {
                        mvnPub.artifact(task);
                    });
                }
            });

            tasks.create("bindingAvroJava", GenerateAvroJavaTask.class, task -> {
                task.setSource(model.getSchema().getSource());
                task.dependsOn("validateAvroSchema");
                task.setOutputDir(model.getGeneratedJava().getSource().getSrcDirs().iterator().next());
                task.setFieldVisibility(model.getBindings().getFieldVisibility());
                task.setStringType(model.getBindings().getStringType());
                task.setCreateSetters(model.getBindings().getCreateSetters().toString());
                task.setOutputCharacterEncoding(model.getBindings().getOutputCharacterEncoding());

                if (model.getBindings().getTemplateDirectory() != null) {
                    task.setTemplateDirectory(model.getBindings().getTemplateDirectory().getAbsolutePath() + File.separator);
                }

                task.setEnabled(model.getBindings().getEnabled());
            });

            project.getTasks().withType(JavaCompile.class, compile -> {
                compile.source(model.getGeneratedJava().getSource());
                compile.dependsOn("zipAvroSchema", "bindingAvroJava");
            });

        }

        @Mutate
        void addGeneratedSrcDirToIdea(ModelMap<Task> tasks, CommsAvroModel model, ExtensionContainer extensions) {

            Project project = extensions.getByType(CommsExtension.class).getProject();
            File generatedSrcDir = generatedAvroSrcDir(project.getBuildDir());

            project.getPlugins().withType(IdeaPlugin.class, idea -> {
                IdeaModule module = idea.getModel().getModule();


                project.getTasks().withType(GenerateIdeaModule.class).all(genTask ->
                        genTask.doFirst(task ->
                                project.mkdir(generatedSrcDir)));

                module.setSourceDirs(Sets.newHashSet(Iterables.concat(
                        module.getSourceDirs(),
                        Collections.singletonList(generatedSrcDir)
                )));

                Set<File> excluded = Sets.newHashSet(module.getExcludeDirs());
                try {
                    project.getBuildDir().mkdirs();
                    excluded.addAll(
                            Files.list(project.getBuildDir().toPath())
                                    .filter(p -> !p.getFileName().toString().startsWith("generated"))
                                    .map(java.nio.file.Path::toFile)
                                    .collect(Collectors.toSet()));
                    excluded.remove(project.getBuildDir());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                module.setExcludeDirs(excluded);

                Set<File> genSrcDirs = module.getGeneratedSourceDirs();
                Set<File> updated = new HashSet<>();
                if (genSrcDirs != null) {
                    updated.addAll(genSrcDirs);
                }
                updated.add(generatedSrcDir);
                module.setGeneratedSourceDirs(updated);
            });
        }


        private File generatedAvroSrcDir(File buildDir) {
            return CommsGradlePlugin.getGeneratedDir(buildDir, "avro");
        }

    }
}
