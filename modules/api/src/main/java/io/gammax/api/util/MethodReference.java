package io.gammax.api.util;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface MethodReference {
    String method();

    Class<?>[] parameters() default {};

    Class<?> result() default void.class;
}