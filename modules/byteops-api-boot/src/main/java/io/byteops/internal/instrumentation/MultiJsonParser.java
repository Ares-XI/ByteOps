package io.byteops.internal.instrumentation;


import com.google.gson.Gson;
import io.byteops.internal.util.ModifyConfigFormat;
import org.jetbrains.annotations.ApiStatus;

import java.util.*;

@ApiStatus.Internal
public abstract class MultiJsonParser {
    private static MultiJsonParser instance;

    public static MultiJsonParser getInstance() {
        return instance;
    }

    protected static void setInstance(MultiJsonParser instance) {
        if (instance.getClass().getName().equals("io.byteops.shadow.ShadowJsonParser")) MultiJsonParser.instance = instance;
    }

    protected final Gson GSON = new Gson();

    public abstract List<ModifyConfigFormat> loadAllModifyConfigs();
}