package com.example.tagihan.handler;

import com.example.tagihan.dispatcher.Handler;
import com.example.tagihan.dispatcher.Messagehandler;
import com.example.tagihan.dto.WebhookPayload;
import reactor.core.publisher.Mono;

@Handler(trigger = "up_tagihan")
public class UpTagihan implements Messagehandler {
    @Override
    public Mono<Void> handle(WebhookPayload message) {
        return null;
    }
}
