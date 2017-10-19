package com.github.jhollandus.gradle.avro;

import avro.shaded.com.google.common.collect.Maps;
import org.apache.avro.Schema;
import org.gradle.api.GradleException;

import java.io.File;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class AvroValidationException extends GradleException {
    private final String message;

    public AvroValidationException(Consumer<Spec> specConsumer) {
        List<String> msgList = new LinkedList<>();
        Spec spec = new Spec();
        specConsumer.accept(spec);
        msgList.add(spec.message);

        if (spec.description != null) {
            msgList.add("");
            Collections.addAll(msgList, spec.description.split("\\r?\\n"));
            msgList.add("");
        }

        spec.props.entrySet().stream().filter(entry -> entry.getValue() != null).map(entry -> {
            String vStr;
            Object val = entry.getValue();

            if(val instanceof Schema) {
                vStr = ((Schema) val).getName();
            } else if(val instanceof File) {
                vStr = ((File) val).getPath();
            } else if(val instanceof Schema.Field) {
                vStr = ((Schema.Field) val).name();
            } else {
                vStr = val.toString();
            }

            return String.format("%s: %s", entry.getKey(), vStr);
        }).forEach(msgList::add);

        this.message = String.join(System.lineSeparator(), msgList);
    }

    @Override
    public String getMessage() {
        return message;
    }

    public class Spec {
        public String message = "No Message Available";
        public String description;
        public Map<String, Object> props = Maps.newLinkedHashMap();
    }
}
