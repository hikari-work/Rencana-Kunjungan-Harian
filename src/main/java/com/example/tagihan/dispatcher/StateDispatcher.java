package com.example.tagihan.dispatcher;

import com.example.tagihan.dto.WebhookPayload;
import com.example.tagihan.service.State;
import com.example.tagihan.service.StateData;
import com.example.tagihan.service.StateService;
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
    private final StateService stateService;

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
                    log.info("Dispatching with StateData for state: {}", state);
                    StateHandlers handler = stateHandlers.get(state);

                    if (handler != null) {
                        log.info("Found handler for state: {}", state);
                        return handler.handle(stateData);
                    }

                    log.warn("No handler found for state: {}", state);
                    return Mono.empty();
                })
                .doOnError(e -> log.error("Error dispatching state: {}", stateData.getCurrentState(), e))
                .onErrorResume(e -> Mono.empty());
    }

    public Mono<Void> handle(WebhookPayload payload) {
        return Mono.defer(() -> {
                    if (payload.getPayload().getChatId().contains("@g.us")) {
                        return Mono.empty();
                    }
                    String chatId = payload.getPayload().getFrom();
                    log.info("Handling payload for chatId: {}", chatId);

                    State state = stateService.getCurrentState(chatId);
                    log.info("Current state for chatId {}: {}", chatId, state);

                    if (state == null) {
                        log.warn("No state found for chatId: {}", chatId);
                        return Mono.empty();
                    }

                    StateHandlers handler = stateHandlers.get(state);

                    if (handler == null) {
                        log.warn("No handler found for state: {}", state);
                        return Mono.empty();
                    }

                    log.info("Found handler for state {}: {}", state, handler.getClass().getSimpleName());

                    return handler.handle(payload)
                            .doOnSubscribe(sub -> log.info("Executing handler for state: {}", state))
                            .doOnSuccess(v -> log.info("Successfully handled state: {}", state))
                            .doOnError(e -> log.error("Error handling state: {}", state, e))
                            .onErrorResume(e -> Mono.empty());
                })
                .doOnError(e -> log.error("Error in handle method", e))
                .onErrorComplete();
    }
}