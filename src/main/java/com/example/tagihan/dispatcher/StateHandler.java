package com.example.tagihan.dispatcher;

import com.example.tagihan.service.State;
import org.springframework.data.mongodb.core.mapping.Document;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Document
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface StateHandler {
    State state();
}
