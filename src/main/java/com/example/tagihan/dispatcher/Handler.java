package com.example.tagihan.dispatcher;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target( { ElementType.TYPE })
public @interface Handler {
    String trigger();

}
