package io.gammax.api.experemental;

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

//    TargetReference reference() default @TargetReference; TODO will be added later

//    Mode mode() default Mode.BEFORE;

//    int index() default 0;
}
