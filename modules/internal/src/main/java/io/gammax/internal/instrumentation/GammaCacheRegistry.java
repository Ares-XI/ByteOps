package io.gammax.internal.instrumentation;

import io.gammax.api.*;
import io.gammax.api.Arg;
import io.gammax.api.experemental.Inject;
import io.gammax.api.Local;
import io.gammax.api.util.InjectResult;
import io.gammax.internal.exeptions.ModifyFormatException;
import io.gammax.internal.exeptions.ModifyInternalException;
import io.gammax.internal.format.*;
import io.gammax.internal.format.data.ArgumentParameter;
//import io.gammax.internal.format.data.LocalParameter; TODO will be added later
import io.gammax.internal.format.data.ProvideField;
import io.gammax.internal.format.data.ProvideMethod;
import io.gammax.internal.format.functional.InjectMethod;
import io.gammax.internal.format.functional.InterfaceImplementation;
import io.gammax.internal.format.functional.ExtendField;
import io.gammax.internal.format.functional.ExtendMethod;
import io.gammax.internal.util.data.GammaConfigFormat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.*;

public final class GammaCacheRegistry {
    public static final GammaCacheRegistry instance = new GammaCacheRegistry();

    private final Set<ModifyClass> modifyClasses = new HashSet<>();

    public ModifyClass[] getCache() {
        return modifyClasses.toArray(new ModifyClass[0]);
    }

    public void clearCache() {
        modifyClasses.clear();
    }

    public boolean isTargetPath(String className) {
        for(ModifyClass modifyClass: GammaCacheRegistry.instance.getCache()) if(modifyClass.getTargetClass().getName().equals(className)) return true;
        return false;
    }

    public void loadCache() {
        List<GammaConfigFormat> parsed = GammaJsonParser.instance.loadAllModifyConfigs();

        System.out.println("Find " + parsed.size() + " gamma.json files");

        if(parsed.toArray().length != 0) System.out.println("Start parsing");
        else {
            System.out.println("Skip parsing");
            return;
        }

        for(GammaConfigFormat format: parsed) {
            for (String path: format.classpath) {
                try {
                   GammaClassLoader.instance.registerClassToDefine(GammaClassLoader.instance.loadClass(path));
                } catch (ClassNotFoundException e) {
                    new ModifyInternalException(e).printStackTrace(System.err);
                }
            }

            for(String path: format.modify) {
                try {
                    System.out.println(path);
                    Class<?> modifyClass = GammaClassLoader.instance.loadClass(path);

                    if(modifyClass.isAnnotation() || modifyClass.isInterface() || modifyClass.isEnum()) {
                        new ModifyFormatException("@Modify class must be abstract").printStackTrace(System.err);
                        continue;
                    }
                    if(!Modifier.isAbstract(modifyClass.getModifiers())) {
                        new ModifyFormatException("@Modify class must be abstract").printStackTrace(System.err);
                        continue;
                    }
                    if(!modifyClass.isAnnotationPresent(Modify.class)) {
                        new ModifyFormatException("class must be annotated by @Modify").printStackTrace(System.err);
                        continue;
                    }

                    List<ProvideField> provideFields = new ArrayList<>();
                    List<ProvideMethod> provideMethods = new ArrayList<>();
                    List<ExtendField> extendFields = new ArrayList<>();
                    List<ExtendMethod> extendMethods = new ArrayList<>();
                    List<InjectMethod> injectMethodsList = new ArrayList<>();
                    List<InterfaceImplementation> interfaceImplementationList = new ArrayList<>();

                    List<Method> tempExtendMethods = new ArrayList<>();
                    List<Method> tempInjectMethods = new ArrayList<>();
                    Map<Method, List<Parameter>> tempArgumentParameters = new HashMap<>();
//                    Map<Method, List<Parameter>> tempLocalParameters = new HashMap<>(); TODO will be added later

                    for(Class<?> interfaceClass: modifyClass.getInterfaces()) {
                        try {
                            GammaClassLoader.instance.loadClass(interfaceClass.getName());
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace(System.err);
                            continue;
                        }

                        interfaceImplementationList.add(new InterfaceImplementation(interfaceClass));
                    }

                    for(Field field: modifyClass.getDeclaredFields()) {
                        if(field.isAnnotationPresent(Provide.class) && field.isAnnotationPresent(Extend.class)) {
                            new ModifyFormatException("field cannot be annotated by @Provide and @Extend").printStackTrace(System.err);
                            continue;
                        }
                        if(field.isAnnotationPresent(Provide.class)) provideFields.add(new ProvideField(field));
                        if(field.isAnnotationPresent(Extend.class)) extendFields.add(new ExtendField(field));
                    }

                    for(Method method: modifyClass.getDeclaredMethods()) {
                        if(method.isAnnotationPresent(Provide.class) && method.isAnnotationPresent(Extend.class)) {
                            new ModifyFormatException("method cannot be annotated by @Provide and @Extend").printStackTrace(System.err);
                            continue;
                        }
                        if(method.isAnnotationPresent(Provide.class) && method.isAnnotationPresent(Inject.class)) {
                            new ModifyFormatException("method cannot be annotated by @Provide and @Inject").printStackTrace(System.err);
                            continue;
                        }
                        if(method.isAnnotationPresent(Extend.class) && method.isAnnotationPresent(Inject.class)) {
                            new ModifyFormatException("method cannot be annotated by @Extend and @Inject").printStackTrace(System.err);
                            continue;
                        }
                        if(method.isAnnotationPresent(Provide.class)) provideMethods.add(new ProvideMethod(method));
                        if(method.isAnnotationPresent(Extend.class)) tempExtendMethods.add(method);
                        if(method.isAnnotationPresent(Inject.class)) {
                            if(!InjectResult.class.isAssignableFrom(method.getReturnType())) {
                                new ModifyFormatException("method must return InjectResult: " + method.getReturnType().getName() + ", " + InjectResult.class.getName()).printStackTrace(System.err);
                                continue;
                            }

                            List<Parameter> args = new ArrayList<>();
//                            List<Parameter> locals = new ArrayList<>(); TODO will be added later
                            boolean isValid = true;

                            for(Parameter arg: method.getParameters()) {
                                if(arg.isAnnotationPresent(Arg.class) && arg.isAnnotationPresent(Local.class)) {
                                    new ModifyFormatException("Inject parameter cannot be annotated by @Argument and @Local").printStackTrace(System.err);
                                    isValid = false;
                                    break;
                                }
                                if(arg.isAnnotationPresent(Arg.class)) args.add(arg);
//                                if(arg.isAnnotationPresent(Local.class)) locals.add(arg); TODO will be added later
                            }

                            if(!isValid) continue;

                            tempArgumentParameters.put(method, args);
//                            tempLocalParameters.put(method, locals); TODO will be added later
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
//                            List<LocalParameter> locals = new ArrayList<>(); TODO will be added later

                            if (tempArgumentParameters.containsKey(method)) {
                                for (Parameter parameter : tempArgumentParameters.get(method)) {
                                    args.add(new ArgumentParameter(parameter));
                                }
                            }

//                            if (tempLocalParameters.containsKey(method)) { TODO will be added later
//                                for (Parameter parameter : tempLocalParameters.get(method)) {
//                                    locals.add(new LocalParameter(parameter));
//                                }
//                            }

                            injectMethodsList.add(new InjectMethod(
                                    method,
                                    targetClass,
                                    provideFields.toArray(new ProvideField[0]),
                                    extendFields.toArray(new ExtendField[0]),
                                    provideMethods.toArray(new ProvideMethod[0]),
                                    extendMethods.toArray(new ExtendMethod[0]),
                                    args.toArray(new ArgumentParameter[0])
//                                    locals.toArray(new LocalParameter[0]) TODO will be added later
                            ));
                        }
                    }

                    GammaClassLoader.instance.loadClass(modifyClass.getAnnotation(Modify.class).value().getName());
                    Class<?> targetClass = modifyClass.getAnnotation(Modify.class).value();

                    ModifyClass modifyClassRef = new ModifyClass(
                            targetClass,
                            provideFields.toArray(new ProvideField[0]),
                            extendFields.toArray(new ExtendField[0]),
                            provideMethods.toArray(new ProvideMethod[0]),
                            extendMethods.toArray(new ExtendMethod[0]),
                            injectMethodsList.toArray(new InjectMethod[0]),
                            interfaceImplementationList.toArray(new InterfaceImplementation[0])
                    );

                    modifyClasses.add(modifyClassRef);

                    System.out.println(Arrays.toString(modifyClassRef.getProvideFields()));
                    System.out.println(Arrays.toString(modifyClassRef.getExtendFields()));
                    System.out.println(Arrays.toString(modifyClassRef.getProvideMethods()));
                    System.out.println(Arrays.toString(modifyClassRef.getExtendMethods()));
                    System.out.println(Arrays.toString(modifyClassRef.getInjectors()));

                } catch (ClassNotFoundException e) {
                    e.printStackTrace(System.err);
                }
            }
        }
    }

    private GammaCacheRegistry() {
        GammaClassLoader.instance.registerClassToDefine(InjectResult.class);
    }
}