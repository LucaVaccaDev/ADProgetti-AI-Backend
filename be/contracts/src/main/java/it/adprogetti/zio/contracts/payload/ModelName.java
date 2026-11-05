package it.adprogetti.zio.contracts.payload;

import java.util.regex.Pattern;

/** Validation of model names (e.g. "qwen3:8b"), so they can never carry paths, spaces or options. */
final class ModelName {

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}");

    private ModelName() {
    }

    static String require(String value) {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid model name");
        }
        return value;
    }
}
