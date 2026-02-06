package com.example.tagihan.util;

import com.example.tagihan.dto.WebhookPayload;

public class CaptionFindUtil {

    public static String caption(WebhookPayload payload) {
        if (payload == null || payload.getPayload() == null) {
            return null;
        }

        if (payload.getPayload().isText()) {
            return payload.getPayload().getBody();
        }

        if (payload.getPayload().getImage() != null) {
            return payload.getPayload().getImage().getCaption();
        }

        return null;
    }
}
