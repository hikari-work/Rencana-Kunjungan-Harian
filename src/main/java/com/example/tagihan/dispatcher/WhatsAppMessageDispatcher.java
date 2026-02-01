package com.example.tagihan.dispatcher;

import com.example.tagihan.dto.WebhookPayload;
import com.example.tagihan.util.CaptionFindUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final ApplicationContext applicationContext;
    private static final String MESSAGE_PREFIX = ".";
    private final Set<String> processingMessages = ConcurrentHashMap.newKeySet();

    private final Map<String, Function<WebhookPayload, Mono<Void>>> config = new HashMap<>();

    @PostConstruct
    public void init() {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(Handler.class);
        beans.forEach((name, bean) -> {
            Handler handler = bean.getClass().getAnnotation(Handler.class);
            String trigger = handler.trigger();

            if (trigger != null && !trigger.isEmpty()) {
                if (bean instanceof Messagehandler) {
                    config.put(trigger, ((Messagehandler) bean)::handle);
                    log.info("Registered handler: {} for trigger: {}", name, trigger);
                } else {
                    log.warn("Handler {} does not implement MessageHandler", name);
                }
            }
        });
    }

    public Mono<Long> dispatch(WebhookPayload message) {
        String caption = CaptionFindUtil.caption(message);
        log.info("Caption: {}", caption);
        String messageId = message.getPayload().getId();
        if (!processingMessages.add(messageId)) {
            log.warn("Message already being processed: {}", messageId);
            return Mono.empty();
        }
        if (caption == null || !caption.startsWith(MESSAGE_PREFIX)) {
            return Mono.empty();
        }

        String[] messageTexts = caption.split(" ", 2);
        String command = messageTexts[0].substring(MESSAGE_PREFIX.length());

        Function<WebhookPayload, Mono<Void>> handler = config.get(command);

        if (handler == null) {
            log.error("No handler found for command: {}", command);
            return Mono.empty();
        }

        return handler.apply(message)
                .onErrorResume(error -> {
                    log.error("Error handling command: {}", command, error);
                    return Mono.empty();
                }).then(Mono.defer(() -> {
                    processingMessages.remove(messageId);
                    return Mono.just(0L);
                }));
    }
}