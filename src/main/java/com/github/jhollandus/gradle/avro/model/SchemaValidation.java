package com.github.jhollandus.gradle.avro.model;

import com.github.jhollandus.gradle.avro.task.AvroSchemaValidate;
import org.gradle.api.Named;
import org.gradle.model.Managed;

@Managed
public interface SchemaValidation extends Named {
    Boolean getEnabled();
    void setEnabled(Boolean enabled);

    AvroSchemaValidate.Compatibility getCompatibility();
    void setCompatibility(AvroSchemaValidate.Compatibility compatibility);

    Boolean getCompareAll();
    void setCompareAll(Boolean compareAll);
}
