package com.example.tagihan.dispatcher;

import com.example.tagihan.service.State;


import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface StateHandler {
    State state();
}
