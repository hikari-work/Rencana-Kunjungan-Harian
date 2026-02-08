package com.example.tagihan.handler;

import com.example.tagihan.dispatcher.Handler;
import com.example.tagihan.dispatcher.StateDispatcher;
import com.example.tagihan.entity.VisitType;
import com.example.tagihan.service.BillsService;
import com.example.tagihan.service.StateService;
import com.example.tagihan.service.WhatsappService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Handler(trigger = "tagihan")
public class Tagihan extends BaseVisitHandler {

    @Value("${message.prefix}")
    private String prefix;

    public Tagihan(StateService stateService, WhatsappService whatsappService, BillsService billsService, StateDispatcher stateDispatcher) {
        super(stateService, whatsappService, billsService, stateDispatcher);
    }

    @Override
    protected VisitType getVisitType() {
        return VisitType.TAGIHAN;
    }

    @Override
    protected String getCommandPrefix() {
        return prefix + "tagihan";
    }
}