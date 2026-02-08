package com.example.tagihan.dispatcher;

import com.example.tagihan.dto.WebhookPayload;
import reactor.core.publisher.Mono;

public interface MessageHandler {
    Mono<Void> handle(WebhookPayload message);
}
