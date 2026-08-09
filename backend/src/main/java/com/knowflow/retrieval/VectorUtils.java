package com.knowflow.retrieval;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class VectorUtils {
    private VectorUtils() {}

    public static String toPgVector(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) builder.append(',');
            builder.append(Float.toString(vector[i]));
        }
        return builder.append(']').toString();
    }

    public static String toPgBigintArray(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(",", "{", "}"));
    }
}
