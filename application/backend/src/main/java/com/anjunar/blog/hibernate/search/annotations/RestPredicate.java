package com.anjunar.blog.hibernate.search.annotations;

import com.anjunar.blog.hibernate.search.PredicateProvider;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface RestPredicate {
    Class<? extends PredicateProvider<?, ?>> value();
    String name() default "";
}
