package cn.ff26710.carsharingapp.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperLogAnnotation {
    String operType();
    String operDesc();
}
