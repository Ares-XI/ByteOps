package io.gammax.api;

import io.gammax.api.util.At;
import io.gammax.api.util.MethodReference;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Inject {
    MethodReference method();

    At at();

    int priority() default 0;

    int index() default 0;
}
