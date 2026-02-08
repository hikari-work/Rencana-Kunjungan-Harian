package com.example.tagihan.handler;

import com.example.tagihan.dispatcher.Handler;
import com.example.tagihan.dispatcher.MessageHandler;
import com.example.tagihan.dto.WebhookPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@Handler(trigger = "rkh")
public class VisitPlanAccountOfficer implements MessageHandler {
    @Override
    public Mono<Void> handle(WebhookPayload message) {

        return Mono.empty();
    }
}
