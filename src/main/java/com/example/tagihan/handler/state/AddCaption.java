package com.example.tagihan.handler.state;

import com.example.tagihan.dispatcher.StateHandler;
import com.example.tagihan.dispatcher.StateHandlers;
import com.example.tagihan.service.State;
import com.example.tagihan.service.StateData;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@StateHandler(state = State.ADD_CAPTION)
public class AddCaption implements StateHandlers {
    @Override
    public Mono<Void> handle(StateData stateData) {
        log.info("Adding caption to visit {}", stateData.getVisit().getId());
        return null;
    }
}
