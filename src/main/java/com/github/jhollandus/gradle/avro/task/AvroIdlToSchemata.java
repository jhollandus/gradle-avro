package com.github.jhollandus.gradle.avro.task;

import avro.shaded.com.google.common.collect.ImmutableMap;
import com.github.jhollandus.gradle.avro.AvroValidationException;
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Schema;
import org.apache.avro.compiler.idl.Idl;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.jhollandus.gradle.Exceptions.asGradleException;
import static java.lang.String.format;

public class AvroIdlToSchemata extends SourceTask {
    public static final String SCHEMA_PROP_REQUIRED = "required";
    public static final String SCHEMA_PROP_EMBEDDED = "embedded";
    public static final String SCHEMA_FIELD_HEADER = "header";
    public static final String SCHEMA_HEADER_NAME = "CommsHeader";
    public static final String IDL_FRAGMENT_EXTENSION = "avdlf";
    public static final String AVRO_IDL_EXTENSION = "avdl";
    public static final String AVRO_SCHEMA_EXTENSION = "avsc";

    private static final Pattern INCLUDE_PATTERN = Pattern.compile("^.*@include\\(['\"](.*?)[\"']\\).*$");

    private Map<String, Schema> namespaceMap = new HashMap<>();
    private Map<String, String> includesCache = new HashMap<>();

    @OutputDirectory
    private File dest;

    public File getDest() {
        return dest;
    }

    public void setDest(File dest) {
        this.dest = dest;
    }

    @TaskAction
    public void transform() {
        //clear out existing schemas
        asGradleException(() ->
            Files.walk(getDest().toPath())
                 .map(Path::toFile)
                 .filter(f -> f.exists() && f.getAbsolutePath().endsWith(AVRO_SCHEMA_EXTENSION))
                 .forEach(f -> {
                     if(!f.delete()) {
                         getLogger().warn("Failed to delete old avro schema file: '{}'", f);
                     }
                 }));

        getSource().filter(f -> f.getName().endsWith("." + AVRO_IDL_EXTENSION)).getFiles()
                .stream()
                .map(this::toIdl)
                .flatMap(idl -> idl.getSchemas().stream())
                .filter(this::validateSchema)
                .filter(schema -> !schema.isEmbedded())
                .forEach(schema ->
                    asGradleException(() -> {
                        try (PrintWriter pw = new PrintWriter(new File(dest, schema.fileName()))) {
                            pw.println(schema.schema.toString(true));
                        }
                }));
    }

    private Boolean validateSchema(SchemaConversion schema) {
        validateFields(schema.idlConversion.idlFile, schema.schema);

        if(!schema.isEmbedded()) {
            validateTopLevelSchema(schema.idlConversion.idlFile, schema.schema);
        }

        return true;
    }

    private Boolean validateTopLevelSchema(File idlFile, Schema schema) {
        Schema.Field header = schema.getField(SCHEMA_FIELD_HEADER);
        if (header == null || !header.schema().getName().equals(SCHEMA_HEADER_NAME)) {
            throw new AvroValidationException(spec -> {
                spec.message = "Header Missing in Record!";
                spec.description = String.join(System.lineSeparator(),
                        "All top level records must contain a 'header' field of type CommsHeader.",
                        "If the record is to be used as an include only then mark it with @embedded(true).");
                spec.props.putAll(ImmutableMap.of("source", idlFile, "schema", schema));
            });
        }

        if (namespaceMap.containsKey(schema.getNamespace())) {

            throw new AvroValidationException(spec -> {
                spec.message = "Top Level Records Share a Namespace!";
                spec.description = String.join(System.lineSeparator(),
                        "Each top level record (not marked with @embedded(true)) must be in their own namespace.",
                        "this is required in order to generate language bindings without causing name collisions.");
                spec.props.putAll(ImmutableMap.of(
                        "source", idlFile,
                        "schema1", schema,
                        "schema2", namespaceMap.get(schema.getNamespace())));
            });
        }

        getLogger().debug("Adding namespace '{}'", schema.getNamespace());
        namespaceMap.put(schema.getNamespace(), schema);

        return true;
    }


    private Boolean validateFields(File idlFile, Schema schema) {
        getLogger().debug("Validating fields for schema '{}'", schema.getName());

        List<Schema.Field> fields = new ArrayList<>();
        try {
            fields = schema.getFields();
        } catch (AvroRuntimeException e) {
            //doesn't have fields
        }

        fields.forEach(field -> {
            getLogger().debug("Validating schema.field for '{}.{}'", schema.getName(), field.name());
            if (field.aliases() != null && !field.aliases().isEmpty()) {
                throw new AvroValidationException(spec -> {
                    spec.message = "Aliases Found!";
                    spec.description = "Aliases are not compatible across avro implementations, please do not use them.";
                    spec.props.put("source", idlFile);
                    spec.props.put("schema", schema);
                    spec.props.put("field", field);
                });
            }

            if (field.getObjectProp(SCHEMA_PROP_REQUIRED) == null &&
                    field.defaultVal() == null &&
                    !nullUnion(field)) {

                throw new AvroValidationException(spec -> {
                    spec.message = "Default Value Missing";
                    spec.description = String.join(System.lineSeparator(),
                            "Unless marked @required(true) (which should rarely be used) a field must have a default value.",
                            "The header field is an exception to this rule in top level records.");
                    spec.props.putAll(ImmutableMap.of(
                            "source", idlFile,
                            "schema", schema,
                            "field", field));
                });
            }
        });

        return true;
    }

    private boolean nullUnion(Schema.Field field) {
        return field.schema().getType() == Schema.Type.UNION &&
                field.schema().getTypes().get(0).getType() == Schema.Type.NULL;
    }

    private String processIncludes(File idlFile) {
        return asGradleException(() -> {
            getLogger().info("Processing idl file {}", idlFile.getPath());
            StringBuilder strBuilder = new StringBuilder(100);

            try (Stream<String> idlStream = Files.lines(idlFile.toPath())) {
                idlStream.map(line -> {
                    Matcher matcher = INCLUDE_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        File include = new File(idlFile.getParentFile(), matcher.group(1));
                        getLogger().debug("Processing include file '{}'", matcher.group(1));
                        if (!include.getPath().endsWith("." + IDL_FRAGMENT_EXTENSION)) {
                            throw new AvroValidationException(spec -> {
                                spec.message = "Invalid Include Statement!";
                                spec.description = format("@include does not specify an avdl fragment (*.%s).", IDL_FRAGMENT_EXTENSION);
                                spec.props.put("source", idlFile);
                                spec.props.put("include", line);
                            });
                        }

                        if (includesCache.containsKey(include.getAbsolutePath())) {
                            getLogger().info("include cache hit '{}'", include.getAbsolutePath());
                            return includesCache.get(include.getAbsolutePath());
                        }

                        return asGradleException(() -> processIncludes(include) + "\n");
                    } else {
                        return line + System.lineSeparator();
                    }
                }).forEach(strBuilder::append);
            }

            String idlStr = strBuilder.toString();
            includesCache.put(idlFile.getAbsolutePath(), idlStr);
            getLogger().info("Resolved IDL File:\n{}", idlStr);
            return idlStr;
        });
    }

    private IdlConversion toIdl(File idlFile) {
        return new IdlConversion(idlFile, new Idl(new StringReader(processIncludes(idlFile))));
    }

    private class IdlConversion {
        final File idlFile;
        final Idl idl;

        public IdlConversion(File idlFile, Idl idl) {
            this.idlFile = idlFile;
            this.idl = idl;
        }

        List<SchemaConversion> getSchemas() {
            return asGradleException(() -> idl.CompilationUnit().getTypes())
                    .stream()
                    .map(schema -> new SchemaConversion(this, schema))
                    .collect(Collectors.toList());
        }
    }

    private class SchemaConversion {
        final IdlConversion idlConversion;
        final Schema schema;

        public SchemaConversion(IdlConversion idlConversion, Schema schema) {
            this.idlConversion = idlConversion;
            this.schema = schema;
        }

        Boolean isEmbedded() {
            return schema.getObjectProp(SCHEMA_PROP_EMBEDDED) != null;
        }

        String fileName() {
            return format("%s.%s", schema.getName(), AvroSchemaValidate.AVRO_SCHEMA_EXTENSION);
        }
    }
}
