package com.example.tagihan.dispatcher;

import com.example.tagihan.dto.WebhookPayload;
import com.example.tagihan.service.State;
import com.example.tagihan.service.StateData;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class StateDispatcher {

    private final ApplicationContext applicationContext;
    private final Map<State, StateHandlers> stateHandlers = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("Initializing StateDispatcher...");

        applicationContext.getBeansWithAnnotation(StateHandler.class)
                .forEach((beanName, bean) -> {
                    StateHandler handler = bean.getClass().getAnnotation(StateHandler.class);

                    if (bean instanceof StateHandlers stateHandler) {
                        stateHandlers.put(handler.state(), stateHandler);
                        log.info("Registered handler for state: {}", handler.state());
                    } else {
                        log.warn("Bean {} has @StateHandler but doesn't implement StateHandlers", beanName);
                    }
                });

        log.info("StateDispatcher initialized with {} handlers", stateHandlers.size());
    }

    public Mono<Void> dispatch(StateData stateData) {
        return Mono.defer(() -> {
                    State state = stateData.getCurrentState();
                    StateHandlers handler = stateHandlers.get(state);

                    if (handler != null) {
                        log.debug("Dispatching to handler for state: {}", state);
                        return handler.handle(stateData);
                    }

                    log.warn("No handler found for state: {}", state);
                    return Mono.empty();
                })
                .doOnError(e -> log.error("Error dispatching state: {}", stateData.getCurrentState(), e))
                .onErrorResume(e -> Mono.empty());
    }
}