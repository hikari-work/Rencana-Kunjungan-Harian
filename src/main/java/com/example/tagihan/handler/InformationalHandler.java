package com.example.tagihan.handler;

import com.example.tagihan.dispatcher.Handler;
import com.example.tagihan.dispatcher.StateDispatcher;
import com.example.tagihan.entity.VisitType;
import com.example.tagihan.service.BillsService;
import com.example.tagihan.service.StateService;
import com.example.tagihan.service.WhatsappService;
import org.springframework.stereotype.Component;

@Component
@Handler(trigger = "janji")
public class InformationalHandler extends BaseVisitHandler{

	public InformationalHandler(StateService stateService, WhatsappService whatsappService, BillsService billsService, StateDispatcher stateDispatcher) {
		super(stateService, whatsappService, billsService, stateDispatcher);
	}

	@Override
	protected VisitType getVisitType() {
		return VisitType.INFORMATIONAL;
	}

	@Override
	protected String getCommandPrefix() {
		return "janji";
	}
}
