package com.github.jhollandus.gradle.avro.task;

import avro.shaded.com.google.common.collect.Lists;
import com.github.jhollandus.gradle.Exceptions;
import com.github.jhollandus.gradle.avro.AvroPluginUtils;
import com.github.jhollandus.gradle.avro.AvroValidationException;
import org.apache.avro.Schema;
import org.apache.avro.SchemaValidationException;
import org.apache.avro.SchemaValidator;
import org.apache.avro.SchemaValidatorBuilder;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.github.jhollandus.gradle.Exceptions.asGradleException;

public class AvroSchemaValidate extends SourceTask {
    public static final String AVRO_SCHEMA_EXTENSION = "avsc";
    private Map<RevTag, PersonIdent> personIdentCache = new HashMap<>();

    private String compatibility;
    private boolean compareAll;

    public AvroSchemaValidate() {
        this.compatibility = Compatibility.BACKWARDS.name();
        this.compareAll = false;
    }

    @TaskAction
    public void validate() {

        if (rootProj().file(".git").exists()) {
            Git git = asGradleException(() -> Git.open(rootProj().file(".")));
            SchemaValidator schemaValidator = createValidator();

            getSource().matching(filter -> filter.include("**/*." + AVRO_SCHEMA_EXTENSION)).forEach(schemaFile -> {
                List<Schema> schemas = asGradleException(() -> findHistoricalSchemas(git, schemaFile));
                Schema localSchema = Exceptions.asGradleException(() -> new Schema.Parser().parse(schemaFile));

                try {
                    schemaValidator.validate(localSchema, Lists.reverse(schemas));
                } catch (SchemaValidationException e) {
                    throw new AvroValidationException(spec -> {
                        spec.description = "Incompatible Schema Modification!";
                        spec.message = e.getMessage();
                        spec.props.put("schema", localSchema);
                        spec.props.put("source", schemaFile);
                    });
                }
            });
        } else {
            throw new GradleException("Not a valid git repository, cannot validate schema compatibility.");
        }
    }

    private List<Schema> findHistoricalSchemas(Git git, File localSchemaFile) throws Exception {

        String schemaGitPath = AvroPluginUtils.relativePath(rootProj().getRootDir(), localSchemaFile);
        List<Schema> schemas = new LinkedList<>();
        for (RevCommit commit : findTags(git)) {
            getLogger().debug(commit.getShortMessage());
            RevTree tree = commit.getTree();

            TreeWalk walker = new TreeWalk(git.getRepository());
            walker.addTree(tree);
            walker.setFilter(PathFilter.create(schemaGitPath));
            walker.setRecursive(true);

            while (walker.next()) {
                if (!walker.getFileMode(0).equals(FileMode.TYPE_TREE)) {
                    ObjectLoader loader = git.getRepository().open(walker.getObjectId(0));
                    try (ObjectStream os = loader.openStream()) {
                        //always use new parser to erase any previous history, schemas are fully contextual
                        schemas.add(new Schema.Parser().parse(os));
                    }
                }
            }
        }

        return schemas;
    }

    private List<RevCommit> findTags(Git git) throws Exception {
        RevWalk walk = new RevWalk(git.getRepository());
        return git.tagList().call().stream()
                .map(ref -> peelRef(git, ref))
                .map(walk::lookupTag)
                .map(rev ->
                    asGradleException(() ->
                        git.log().add(rev).call().iterator().next()))
                .sorted(Comparator.comparingInt(RevCommit::getCommitTime))
                .collect(Collectors.toList());
    }

    private ObjectId peelRef(Git git, Ref ref) {
        Ref peeledRef = git.getRepository().peel(ref);
        if(peeledRef.getPeeledObjectId() != null) {
            return peeledRef.getPeeledObjectId();
        } else {
            return ref.getObjectId();
        }
    }

    private Project rootProj() {
        return getProject().getRootProject();
    }

    private SchemaValidator createValidator() {
        SchemaValidatorBuilder builder = new SchemaValidatorBuilder();

        switch (AvroSchemaValidate.Compatibility.valueOf(compatibility.toUpperCase())) {
            case FORWARDS:
                builder.canReadStrategy();
                break;
            case FULL:
                builder.mutualReadStrategy();
                break;
            case BACKWARDS:
            default:
                builder.canBeReadStrategy();
                break;
        }

        if (compareAll) {
            return builder.validateAll();
        } else {
            return builder.validateLatest();
        }
    }

    @Input
    public String getCompatibility() {
        return compatibility;
    }

    public void setCompatibility(String compatibility) {
        this.compatibility = compatibility;
    }

    @Input
    public boolean isCompareAll() {
        return compareAll;
    }

    public void setCompareAll(boolean compareAll) {
        this.compareAll = compareAll;
    }

    public enum Compatibility {FULL, BACKWARDS, FORWARDS}
}
