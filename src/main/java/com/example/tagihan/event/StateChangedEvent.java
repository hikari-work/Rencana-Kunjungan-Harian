package com.example.tagihan.event;

import com.example.tagihan.service.StateData;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class StateChangedEvent extends ApplicationEvent {

    private final StateData stateData;
    public StateChangedEvent(Object source, StateData stateData) {
        super(source);
        this.stateData = stateData;
    }
}
