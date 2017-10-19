package com.github.jhollandus.gradle.avro;

import java.io.File;
import java.util.regex.Pattern;

import static java.lang.String.format;

public class AvroPluginUtils {
    private AvroPluginUtils() {
    }

    public static String relativePath(File root, File target) {
        String prefix = format("%s\\/?", Pattern.quote(root.getAbsolutePath()));
        return target.getAbsolutePath().replaceFirst(prefix, "");
    }
}
