package io.byteops.internal.util;

import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public class ModifyConfigFormat {
    public List<String> modify;
    public List<String> classpath = new ArrayList<>();
}