package com.example.tagihan.util;

import com.example.tagihan.dto.WebhookPayload;

public class CaptionFindUtil {

    public static String caption(WebhookPayload payload) {
        return payload.getPayload().isText() ? payload.getPayload().getBody() : payload.getPayload().getImage().getCaption();
    }
}
