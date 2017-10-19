package com.github.jhollandus.gradle.avro.model;

import org.gradle.api.Named;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.java.JavaSourceSet;
import org.gradle.model.Managed;

@Managed
public interface CommsAvroModel extends Named {
    LanguageSourceSet getIdl();
    //void setIdl(LanguageSourceSet avroIdlSourceSet);

    LanguageSourceSet getSchema();
    //void setSchema(LanguageSourceSet avroSchemaSourceSet);

    JavaSourceSet getGeneratedJava();

    JavaBinding getBindings();
    //void setBindings(JavaBinding javaBinding);

    SchemaValidation getValidation();
    //void setValidation(SchemaValidation validation);
}
