package com.anjunar.blog.hibernate.search.annotations;

import com.anjunar.blog.hibernate.search.SortProvider;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface RestSort {
    Class<? extends SortProvider<?, ?>> value();
}
