package io.gammax.api;

import io.gammax.api.util.At;
import io.gammax.api.util.Signature;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Inject {
    String method();

    At at();

    Signature signature() default @Signature;

    int priority() default 0;

    int index() default 0;
}
