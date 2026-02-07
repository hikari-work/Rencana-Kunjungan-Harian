package com.example.tagihan.handler;

import com.example.tagihan.dispatcher.Handler;
import com.example.tagihan.dispatcher.Messagehandler;
import com.example.tagihan.dto.WebhookPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@Handler(trigger = "rkh")
public class VisitPlanAccountOfficer implements Messagehandler {
    @Override
    public Mono<Void> handle(WebhookPayload message) {

        return Mono.empty();
    }
}
