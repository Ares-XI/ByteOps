package io.gammax.api.experemental;

import io.gammax.api.util.At;
import io.gammax.api.util.Signature;
import io.gammax.api.util.Mode;
import io.gammax.api.util.TargetReference;

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

//    TargetReference reference() default @TargetReference;

//    Mode mode() default Mode.BEFORE;

//    int index() default 0;
}
