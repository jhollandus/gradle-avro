package com.github.jhollandus.gradle.avro.model;

import org.apache.avro.compiler.specific.SpecificCompiler;
import org.apache.avro.generic.GenericData;
import org.gradle.api.Named;
import org.gradle.model.Managed;

import java.io.File;
import java.nio.charset.Charset;

@Managed
public interface JavaBinding extends Named {
    Boolean getEnabled();
    void setEnabled(Boolean enabled);

    String getOutputCharacterEncoding();
    void setOutputCharacterEncoding(String outputCharacterEncoding);

    GenericData.StringType getStringType();
    void setStringType(GenericData.StringType stringType);

    SpecificCompiler.FieldVisibility getFieldVisibility();
    void setFieldVisibility(SpecificCompiler.FieldVisibility fieldVisibility);

    File getTemplateDirectory();

    void setTemplateDirectory(File templateDirectory);

    Boolean getCreateSetters();
    void setCreateSetters(Boolean createSetters);
}
