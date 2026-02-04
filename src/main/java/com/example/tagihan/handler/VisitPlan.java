package com.example.tagihan.handler;

import com.example.tagihan.dispatcher.Handler;
import com.example.tagihan.dispatcher.Messagehandler;
import com.example.tagihan.dto.WebhookPayload;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Handler(trigger = "rkh")
public class VisitPlan implements Messagehandler {
    @Override
    public Mono<Void> handle(WebhookPayload message) {
        return Mono.empty();
    }
}
