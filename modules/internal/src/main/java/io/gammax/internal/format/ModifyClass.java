package io.gammax.internal.format;

import io.gammax.internal.format.data.ProvideField;
import io.gammax.internal.format.data.ProvideMethod;
import io.gammax.internal.format.functional.InjectMethod;
import io.gammax.internal.format.functional.InterfaceImplementation;
import io.gammax.internal.format.functional.ExtendField;
import io.gammax.internal.format.functional.ExtendMethod;
import org.jetbrains.annotations.NotNull;

public final class ModifyClass {
    private final Class<?> targetClass;

    private final ProvideField[] provideFields;

    private final ExtendField[] extendFields;

    private final ProvideMethod[] provideMethods;

    private final ExtendMethod[] extendMethods;

    private final InjectMethod[] injectMethods;

    private final InterfaceImplementation[] implementations;

    public ModifyClass(
            Class<?> targetClass,
            ProvideField[] provideFields,
            ExtendField[] extendFields,
            ProvideMethod[] provideMethods,
            ExtendMethod[] extendMethods,
            InjectMethod[] injectMethods,
            InterfaceImplementation[] interfaceImplementations
    ) {
        this.targetClass = targetClass;
        this.provideFields = provideFields;
        this.extendFields = extendFields;
        this.provideMethods = provideMethods;
        this.extendMethods = extendMethods;
        this.injectMethods = injectMethods;
        this.implementations = interfaceImplementations;
    }

    public Class<?> getTargetClass() {
        return targetClass;
    }

    public ProvideField[] getProvideFields() {
        return provideFields;
    }

    public ExtendField[] getExtendFields() {
        return extendFields;
    }

    public ProvideMethod[] getProvideMethods() {
        return provideMethods;
    }

    public ExtendMethod[] getExtendMethods() {
        return extendMethods;
    }

    public InjectMethod[] getInjectors() {
        return injectMethods;
    }

    public InterfaceImplementation[] getImplementations() {
        return implementations;
    }
}
