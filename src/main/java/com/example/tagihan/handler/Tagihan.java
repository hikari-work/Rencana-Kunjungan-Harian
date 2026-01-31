package com.example.tagihan.handler;

import com.example.tagihan.dispatcher.Handler;
import com.example.tagihan.dispatcher.Messagehandler;
import com.example.tagihan.dto.WebhookPayload;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Handler(trigger = "tagihan")
public class Tagihan implements Messagehandler {
    @Override
    public Mono<Void> handle(WebhookPayload message) {
        System.out.println(message);
        return Mono.empty();
    }
}
