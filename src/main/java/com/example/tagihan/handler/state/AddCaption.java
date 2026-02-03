package com.example.tagihan.handler.state;

import com.example.tagihan.dispatcher.StateHandler;
import com.example.tagihan.dispatcher.StateHandlers;
import com.example.tagihan.dto.WebhookPayload;
import com.example.tagihan.entity.Visit;
import com.example.tagihan.service.State;
import com.example.tagihan.service.StateData;
import reactor.core.publisher.Mono;

@StateHandler(state = State.ADD_CAPTION)
public class AddCaption implements StateHandlers {
    @Override
    public Mono<Void> handle(StateData stateData) {

        return null;
    }
}
