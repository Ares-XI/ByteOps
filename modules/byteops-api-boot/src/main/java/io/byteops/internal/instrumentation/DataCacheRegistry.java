package io.byteops.internal.instrumentation;

import io.byteops.internal.format.ModifyClass;
import org.jetbrains.annotations.ApiStatus;

import java.util.*;

@ApiStatus.Internal
public abstract class DataCacheRegistry {
    private static DataCacheRegistry instance;

    public static DataCacheRegistry getInstance() {
        return instance;
    }

    protected static void setInstance(DataCacheRegistry instance) {
        if(instance.getClass().getName().equals("io.byteops.shadow.ShadowCacheRegistry")) DataCacheRegistry.instance = instance;
    }

    protected final Set<ModifyClass> modifyClasses = new HashSet<>();

    public ModifyClass[] getCache() {
        return modifyClasses.toArray(new ModifyClass[0]);
    }

    protected void clear() {
        modifyClasses.clear();
    }

    public boolean isTargetPath(String className) {
        for(ModifyClass modifyClass: DataCacheRegistry.instance.getCache()) if(modifyClass.getTargetClass().getName().equals(className)) return true;
        return false;
    }
}