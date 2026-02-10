package com.example.tagihan.dispatcher;

import com.example.tagihan.dto.WebhookPayload;
import com.example.tagihan.handler.state.CompletedVisitState;
import com.example.tagihan.service.State;
import com.example.tagihan.service.StateData;
import com.example.tagihan.service.StateService;
import com.example.tagihan.util.CaptionFindUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppMessageDispatcher {

    @Value("${message.prefix}")
    private String messagePrefix;

    private final ApplicationContext applicationContext;
    private final Set<String> processingMessages = ConcurrentHashMap.newKeySet();

    private final Map<String, Function<WebhookPayload, Mono<Void>>> config = new HashMap<>();
    private final StateService stateService;
    private final StateDispatcher stateDispatcher;
    private final CompletedVisitState completedVisitState;

    @PostConstruct
    public void init() {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(Handler.class);
        beans.forEach((name, bean) -> {
            Handler handler = bean.getClass().getAnnotation(Handler.class);
            String trigger = handler.trigger();

            if (trigger != null && !trigger.isEmpty()) {
                if (bean instanceof MessageHandler) {
                    config.put(trigger, ((MessageHandler) bean)::handle);
                } else {
                    log.warn("Handler {} does not implement MessageHandler", name);
                }
            }
        });
    }
    public Mono<Void> handle(StateData stateData) {
        if (stateData.getCurrentState().equals(State.COMPLETED)) {
            return completedVisitState.handle(stateData).then();
        }
        return Mono.empty();
    }

    public Mono<Long> dispatch(WebhookPayload message) {
        String caption = CaptionFindUtil.caption(message);
        log.info("Caption: {}", caption);
        log.info("Processing message From: {}", message.getPayload().getFrom());

        StateData userState = stateService.getUserState(message.getPayload().getFrom());

        if (userState != null) {
            log.info("User has active state: {}", userState.getCurrentState());

            return stateDispatcher.handle(message)
                    .doOnSubscribe(sub -> log.info("Dispatching to state handler"))
                    .doOnSuccess(v -> log.info("State handler completed successfully"))
                    .doOnError(error -> log.error("Error handling state update: {}", userState.getCurrentState(), error))
                    .then(Mono.just(0L));
        }

        String messageId = message.getPayload().getId();

        if (!processingMessages.add(messageId)) {
            log.warn("Message already being processed: {}", messageId);
            return Mono.empty();
        }

        if (caption == null || !caption.startsWith(messagePrefix)) {
            processingMessages.remove(messageId);
            return Mono.empty();
        }

        String[] messageTexts = caption.split(" ", 2);
        String command = messageTexts[0].substring(messagePrefix.length());

        Function<WebhookPayload, Mono<Void>> handler = config.get(command);

        if (handler == null) {
            log.error("No handler found for command: {}", command);
            processingMessages.remove(messageId);
            return Mono.empty();
        }

        return handler.apply(message)
                .onErrorResume(error -> {
                    log.error("Error handling command: {}", command, error);
                    return Mono.empty();
                })
                .then(Mono.defer(() -> Mono.just(0L)))
                .doFinally(signalType -> processingMessages.remove(messageId));
    }
}