package io.byteops.internal.instrumentation;


import com.google.gson.Gson;
import io.byteops.internal.util.ModifyConfigFormat;
import io.byteops.shadow.ShadowUtils;
import org.jetbrains.annotations.ApiStatus;

import java.util.*;

@ApiStatus.Internal
public abstract class MultiJsonParser {
    private static MultiJsonParser instance;

    public static MultiJsonParser getInstance() {
        return instance;
    }

    protected static void setInstance(MultiJsonParser instance) {
        if (instance.getClass().equals(ShadowUtils.SHADOW_JSON_PARSER)) MultiJsonParser.instance = instance;
    }

    protected final Gson GSON = new Gson();

    public abstract List<ModifyConfigFormat> loadAllModifyConfigs();
}