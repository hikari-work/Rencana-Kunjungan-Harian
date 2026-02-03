package com.example.tagihan.dispatcher;

import com.example.tagihan.dto.WebhookPayload;
import com.example.tagihan.service.StateData;
import reactor.core.publisher.Mono;

public interface StateHandlers {
    Mono<Void> handle(StateData stateData);
}
