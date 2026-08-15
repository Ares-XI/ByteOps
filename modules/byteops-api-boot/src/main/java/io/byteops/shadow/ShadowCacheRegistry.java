package io.byteops.shadow;

import io.byteops.internal.InternalBootManager;
import io.byteops.internal.exceptions.ModifyFormatException;
import io.byteops.internal.exceptions.ModifyInternalException;
import io.byteops.internal.format.ModifyClass;
import io.byteops.internal.format.data.ArgumentParameter;
import io.byteops.internal.format.data.LocalParameter;
import io.byteops.internal.format.data.ProvideField;
import io.byteops.internal.format.data.ProvideMethod;
import io.byteops.internal.format.functional.ExtendField;
import io.byteops.internal.format.functional.ExtendMethod;
import io.byteops.internal.format.functional.Injector;
import io.byteops.internal.format.functional.InterfaceImplementation;
import io.byteops.internal.instrumentation.DataCacheRegistry;
import io.byteops.internal.instrumentation.JarClassLoader;
import io.byteops.internal.instrumentation.MultiJsonParser;
import io.byteops.internal.util.ModifyConfigFormat;
import io.byteops.modify.*;
import io.byteops.modify.util.InjectResult;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.*;

final class ShadowCacheRegistry extends DataCacheRegistry {
    static final ShadowCacheRegistry instance = new ShadowCacheRegistry();

    static void init() {
        setInstance(instance);
    }

    void clearCache() {
        super.clear();
    }

    public void loadCache() {
        List<ModifyConfigFormat> parsed = MultiJsonParser.getInstance().loadAllModifyConfigs();

        InternalBootManager.getInstance().getPrintStream().println("Find " + parsed.size() + " " + InternalBootManager.getInstance().getJsonName() + ".json files");

        if(parsed.toArray().length != 0) {
            InternalBootManager.getInstance().getPrintStream().println("Start parsing");
            if(InternalBootManager.getInstance().isLogParser()) InternalBootManager.getInstance().getPrintStream().println();
        }
        else {
            InternalBootManager.getInstance().getPrintStream().println("Skip parsing");
            return;
        }

        for(ModifyConfigFormat format: parsed) {
            for (String path: format.classpath) {
                try {
                    ShadowClassLoader.instance.registerClassToDefine(JarClassLoader.getInstance().loadClass(path));
                } catch (ClassNotFoundException e) {
                    new ModifyInternalException(e).printStackTrace(InternalBootManager.getInstance().getPrintStream());
                }
            }

            for(String path: format.modify) {
                try {
                    if(InternalBootManager.getInstance().isLogParser()) {
                        InternalBootManager.getInstance().getPrintStream().println("[Parser]: visted class: " + path);
                        InternalBootManager.getInstance().getPrintStream().println();
                    }
                    Class<?> modifyClass = JarClassLoader.getInstance().loadClass(path);

                    if(modifyClass.isAnnotation() || modifyClass.isInterface() || modifyClass.isEnum()) {
                        new ModifyFormatException("@Modify class must be abstract").printStackTrace(InternalBootManager.getInstance().getPrintStream());
                        continue;
                    }
                    if(!Modifier.isAbstract(modifyClass.getModifiers())) {
                        new ModifyFormatException("@Modify class must be abstract").printStackTrace(InternalBootManager.getInstance().getPrintStream());
                        continue;
                    }
                    if(!modifyClass.isAnnotationPresent(Modify.class)) {
                        new ModifyFormatException("class must be annotated by @Modify").printStackTrace(InternalBootManager.getInstance().getPrintStream());
                        continue;
                    }

                    List<ProvideField> provideFields = new ArrayList<>();
                    List<ProvideMethod> provideMethods = new ArrayList<>();
                    List<ExtendField> extendFields = new ArrayList<>();
                    List<ExtendMethod> extendMethods = new ArrayList<>();
                    List<Injector> injectMethodsList = new ArrayList<>();
                    List<InterfaceImplementation> interfaceImplementationList = new ArrayList<>();

                    List<Method> tempExtendMethods = new ArrayList<>();
                    List<Method> tempInjectMethods = new ArrayList<>();
                    Map<Method, List<Parameter>> tempArgumentParameters = new HashMap<>();
                    Map<Method, List<Parameter>> tempLocalParameters = new HashMap<>();

                    for(Class<?> interfaceClass: modifyClass.getInterfaces()) {
                        try {
                            JarClassLoader.getInstance().loadClass(interfaceClass.getName());
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace(InternalBootManager.getInstance().getPrintStream());
                            continue;
                        }

                        interfaceImplementationList.add(new InterfaceImplementation(interfaceClass));
                    }

                    for(Field field: modifyClass.getDeclaredFields()) {
                        if(field.isAnnotationPresent(Provide.class) && field.isAnnotationPresent(Extend.class)) {
                            new ModifyFormatException("field cannot be annotated by @Provide and @Extend").printStackTrace(InternalBootManager.getInstance().getPrintStream());
                            continue;
                        }
                        if(field.isAnnotationPresent(Provide.class)) provideFields.add(new ProvideField(field));
                        if(field.isAnnotationPresent(Extend.class)) extendFields.add(new ExtendField(field));
                    }

                    for(Method method: modifyClass.getDeclaredMethods()) {
                        if(method.isAnnotationPresent(Provide.class) && method.isAnnotationPresent(Extend.class)) {
                            new ModifyFormatException("method cannot be annotated by @Provide and @Extend").printStackTrace(InternalBootManager.getInstance().getPrintStream());
                            continue;
                        }
                        if(method.isAnnotationPresent(Provide.class) && method.isAnnotationPresent(Inject.class)) {
                            new ModifyFormatException("method cannot be annotated by @Provide and @Inject").printStackTrace(InternalBootManager.getInstance().getPrintStream());
                            continue;
                        }
                        if(method.isAnnotationPresent(Extend.class) && method.isAnnotationPresent(Inject.class)) {
                            new ModifyFormatException("method cannot be annotated by @Extend and @Inject").printStackTrace(InternalBootManager.getInstance().getPrintStream());
                            continue;
                        }
                        if(method.isAnnotationPresent(Provide.class)) provideMethods.add(new ProvideMethod(method));
                        if(method.isAnnotationPresent(Extend.class)) tempExtendMethods.add(method);
                        if(method.isAnnotationPresent(Inject.class)) {
                            if(!InjectResult.class.isAssignableFrom(method.getReturnType())) {
                                new ModifyFormatException("method must return InjectResult: " + method.getReturnType().getName() + ", " + InjectResult.class.getName()).printStackTrace(InternalBootManager.getInstance().getPrintStream());
                                continue;
                            }

                            List<Parameter> args = new ArrayList<>();
                            List<Parameter> locals = new ArrayList<>();
                            boolean isValid = true;

                            for(Parameter arg: method.getParameters()) {
                                if(arg.isAnnotationPresent(Arg.class) && arg.isAnnotationPresent(Local.class)) {
                                    new ModifyFormatException("Inject parameter cannot be annotated by @Argument and @Local").printStackTrace(InternalBootManager.getInstance().getPrintStream());
                                    isValid = false;
                                    break;
                                }
                                if(arg.isAnnotationPresent(Arg.class)) args.add(arg);
                                if(arg.isAnnotationPresent(Local.class)) locals.add(arg);
                            }

                            if(!isValid) continue;

                            tempArgumentParameters.put(method, args);
                            tempLocalParameters.put(method, locals);
                            tempInjectMethods.add(method);
                        }
                    }

                    if(modifyClass.isAnnotationPresent(Modify.class)) {
                        Class<?> targetClass = modifyClass.getAnnotation(Modify.class).value();
                        for (Method method : tempExtendMethods) {
                            extendMethods.add(
                                    new ExtendMethod(
                                            method, targetClass,
                                            provideFields.toArray(new ProvideField[0]),
                                            provideMethods.toArray(new ProvideMethod[0]),
                                            extendFields.toArray(new ExtendField[0]),
                                            new ExtendMethod[0]
                                    )
                            );
                        }
                        for (ExtendMethod um : extendMethods) um.updateMethodMap(extendMethods.toArray(new ExtendMethod[0]));
                        for (Method method : tempInjectMethods) {
                            List<ArgumentParameter> args = new ArrayList<>();
                            List<LocalParameter> locals = new ArrayList<>();

                            if (tempArgumentParameters.containsKey(method)) {
                                for (Parameter parameter : tempArgumentParameters.get(method)) {
                                    args.add(new ArgumentParameter(parameter));
                                }
                            }

                            if (tempLocalParameters.containsKey(method)) {
                                for (Parameter parameter : tempLocalParameters.get(method)) {
                                    locals.add(new LocalParameter(parameter));
                                }
                            }

                            injectMethodsList.add(new Injector(
                                    method,
                                    targetClass,
                                    provideFields.toArray(new ProvideField[0]),
                                    extendFields.toArray(new ExtendField[0]),
                                    provideMethods.toArray(new ProvideMethod[0]),
                                    extendMethods.toArray(new ExtendMethod[0]),
                                    args.toArray(new ArgumentParameter[0]),
                                    locals.toArray(new LocalParameter[0])
                            ));
                        }
                    }

                    JarClassLoader.getInstance().loadClass(modifyClass.getAnnotation(Modify.class).value().getName());
                    Class<?> targetClass = modifyClass.getAnnotation(Modify.class).value();

                    ModifyClass modifyClassRef = new ModifyClass(
                            modifyClass,
                            targetClass,
                            provideFields.toArray(new ProvideField[0]),
                            extendFields.toArray(new ExtendField[0]),
                            provideMethods.toArray(new ProvideMethod[0]),
                            extendMethods.toArray(new ExtendMethod[0]),
                            injectMethodsList.toArray(new Injector[0]),
                            interfaceImplementationList.toArray(new InterfaceImplementation[0])
                    );

                    modifyClasses.add(modifyClassRef);

                    if(InternalBootManager.getInstance().isLogParser()) {
                        for (ProvideField field : modifyClassRef.getProvideFields()) {
                            InternalBootManager.getInstance().getPrintStream().println("[Parser]: serialize ProvideField: ");
                            InternalBootManager.getInstance().getPrintStream().println("- name: " + field.field().getName());
                            InternalBootManager.getInstance().getPrintStream().println("- type: " + field.field().getType());
                            InternalBootManager.getInstance().getPrintStream().println();
                        }
                        for (ProvideMethod method : modifyClassRef.getProvideMethods()) {
                            InternalBootManager.getInstance().getPrintStream().println("[Parser]: serialize ProvideMethod: ");
                            InternalBootManager.getInstance().getPrintStream().println("- name: " + method.method().getName());
                            InternalBootManager.getInstance().getPrintStream().println("- parameters: " + Arrays.toString(method.method().getParameters()));
                            InternalBootManager.getInstance().getPrintStream().println("- return type: " + method.method().getReturnType());
                            InternalBootManager.getInstance().getPrintStream().println();
                        }
                        for (ExtendField field : modifyClassRef.getExtendFields()) {
                            InternalBootManager.getInstance().getPrintStream().println("[Parser]: serialize ExtendField: ");
                            InternalBootManager.getInstance().getPrintStream().println("- name: " + field.getField().getName());
                            InternalBootManager.getInstance().getPrintStream().println("- type: " + field.getField().getType());
                            InternalBootManager.getInstance().getPrintStream().println();
                        }
                        for (ExtendMethod method : modifyClassRef.getExtendMethods()) {
                            InternalBootManager.getInstance().getPrintStream().println("[Parser]: serialize ExtendMethod: ");
                            InternalBootManager.getInstance().getPrintStream().println("- name: " + method.getMethod().getName());
                            InternalBootManager.getInstance().getPrintStream().println("- parameters: " + Arrays.toString(method.getMethod().getParameters()));
                            InternalBootManager.getInstance().getPrintStream().println("- return type: " + method.getMethod().getReturnType());
                            InternalBootManager.getInstance().getPrintStream().println();
                        }
                        for (InterfaceImplementation implementation : modifyClassRef.getImplementations()) {
                            InternalBootManager.getInstance().getPrintStream().println("[Parser]: serialize Implementation: ");
                            InternalBootManager.getInstance().getPrintStream().println("- interface: " + implementation.getClass().getName());
                            InternalBootManager.getInstance().getPrintStream().println();
                        }
                        for (Injector injector : modifyClassRef.getInjectors()) {
                            InternalBootManager.getInstance().getPrintStream().println("[Parser]: serialize Injector: ");
                            InternalBootManager.getInstance().getPrintStream().println("- method: " + injector.getAnnotation().method());
                            InternalBootManager.getInstance().getPrintStream().println("- - method: " + injector.getAnnotation().method().method());
                            InternalBootManager.getInstance().getPrintStream().println("- - parameters: " + Arrays.toString(injector.getAnnotation().method().parameters()));
                            InternalBootManager.getInstance().getPrintStream().println("- - result: " + injector.getAnnotation().method().result());
                            InternalBootManager.getInstance().getPrintStream().println("- point: " + injector.getAnnotation().at());
                            InternalBootManager.getInstance().getPrintStream().println("- index: " + injector.getAnnotation().index());
                            InternalBootManager.getInstance().getPrintStream().println("- priority: " + injector.getPriority());
                            InternalBootManager.getInstance().getPrintStream().println();
                        }
                    }

                } catch (ClassNotFoundException e) {
                    e.printStackTrace(InternalBootManager.getInstance().getPrintStream());
                }
            }
        }
    }
}
