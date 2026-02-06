package com.example.tagihan.util;

import com.example.tagihan.dto.WebhookPayload;

public class CaptionFindUtil {

    public static String caption(WebhookPayload payload) {
        if (payload.getPayload().isText()) {
            return payload.getPayload().getBody();
        } else {
            return payload.getPayload().getImage().getCaption();
        }
    }
}
