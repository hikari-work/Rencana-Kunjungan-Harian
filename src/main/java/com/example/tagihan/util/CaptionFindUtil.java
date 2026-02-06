package com.example.tagihan.util;

import com.example.tagihan.dto.WebhookData;
import com.example.tagihan.dto.WebhookPayload;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CaptionFindUtil {

    public static String caption(WebhookPayload payload) {
        log.info("Caption : {}", payload.toString());

        WebhookData data = payload.getPayload();

        if (data.isText()) {
            return data.getBody();
        }

        if (data.isImage() && data.getImage() != null) {
            return data.getImage().getCaption();
        }

        log.warn("Tidak ada caption tersedia untuk payload: {}", payload);
        return "";
    }
}
