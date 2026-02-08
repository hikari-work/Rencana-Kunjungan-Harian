package com.example.tagihan.handler.state;

import com.example.tagihan.dispatcher.StateHandler;
import com.example.tagihan.dispatcher.StateHandlers;
import com.example.tagihan.dto.WebhookPayload;
import com.example.tagihan.dto.WhatsAppMessageType;
import com.example.tagihan.dto.WhatsAppRequestDTO;
import com.example.tagihan.entity.Visit;
import com.example.tagihan.entity.VisitType;
import com.example.tagihan.service.*;
import com.example.tagihan.util.CurrencyUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.Transient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@StateHandler(state = State.COMPLETED)
public class CompletedVisitState implements StateHandlers {
    private final StateService stateService;
    private final VisitService visitService;
    @Transient
    private final WhatsappService whatsappService;

    public CompletedVisitState(StateService stateService, VisitService visitService, WhatsappService whatsappService) {
        this.stateService = stateService;
        this.visitService = visitService;
        this.whatsappService = whatsappService;
    }

    @Override
    public Mono<Void> handle(StateData stateData) {
        String jid = stateData.getVisit().getUserId();
        VisitType visitType = stateData.getVisit().getVisitType();

        log.info("Processing completed {} visit for user: {}", visitType, jid);

        return visitService.save(stateData.getVisit())
                .doOnSuccess(visit -> {
                    assert visit != null;
                    log.info("Visit saved successfully - Type: {}, ID: {}", visitType, visit.getId());
                    stateService.removeState(jid);
                })
                .flatMap(visit -> sendSuccessMessage(jid, visit))
                .doOnError(error -> log.error("Failed to complete {} visit for user: {}", visitType, jid, error))
                .onErrorResume(error -> sendErrorMessage(jid, visitType))
                .then();
    }

    @Override
    public Mono<Void> handle(WebhookPayload message) {
        return Mono.empty();
    }

    private Mono<Void> sendSuccessMessage(String jid, Visit visit) {
        VisitType visitType = visit.getVisitType();
        String message = buildSuccessMessage(visitType, visit);

        WhatsAppRequestDTO request = WhatsAppRequestDTO.builder()
                .phone(jid)
                .message(message)
                .type(WhatsAppMessageType.TEXT)
                .build();

        return whatsappService.sendMessage(request)
                .doOnSubscribe(sub -> log.info("Sending success message for {} to {}", visitType, jid))
                .doOnSuccess(v -> log.info("Success message sent for {}", visitType))
                .then();
    }

    private String buildSuccessMessage(VisitType visitType, Visit visit) {
        return switch (visitType) {
            case TAGIHAN -> buildTagihanSuccessMessage(visit);
            case MONITORING -> buildMonitoringSuccessMessage(visit);
            case CANVASING -> buildCanvasingSuccessMessage(visit);
            case SURVEY -> buildSurveySuccessMessage(visit);
        };
    }


    private String buildTagihanSuccessMessage(Visit visit) {
        StringBuilder message = new StringBuilder();
        message.append("✅ *Data Tagihan Berhasil Disimpan*\n\n");
        message.append("📋 *Detail Tagihan:*\n");

        builder(visit, message);
        if (visit.getReminderDate() != null) {
            message.append("• Reminder: ").append(visit.getReminderDate()).append("\n");
        }
        if (visit.getAppointment() != null) {
            message.append("• Janji Bayar: ").append(CurrencyUtil.formatRupiah(visit.getAppointment())).append("\n");
        }

        message.append("\nTerima kasih! Data tagihan telah tersimpan di sistem.");

        return message.toString();
    }

    private void builder(Visit visit, StringBuilder message) {
        if (visit.getSpk() != null) {
            message.append("• SPK: ").append(visit.getSpk()).append("\n");
        }
        if (visit.getName() != null) {
            message.append("• Nama: ").append(visit.getName()).append("\n");
        }
        if (visit.getNote() != null) {
            message.append("• Catatan: ").append(visit.getNote()).append("\n");
        }
    }

    private String buildMonitoringSuccessMessage(Visit visit) {
        StringBuilder message = new StringBuilder();
        message.append("✅ *Data Monitoring Berhasil Disimpan*\n\n");
        message.append("📊 *Detail Monitoring:*\n");

        builder(visit, message);
        if (visit.getUsaha() != null) {
            message.append("• Kondisi Usaha: ").append(visit.getUsaha()).append("\n");
        }

        message.append("\nTerima kasih! Data monitoring telah tersimpan di sistem.");

        return message.toString();
    }

    private String buildCanvasingSuccessMessage(Visit visit) {
        StringBuilder message = new StringBuilder();
        message.append("✅ *Data Canvasing Berhasil Disimpan*\n\n");
        message.append("🎯 *Detail Canvasing:*\n");

        if (visit.getName() != null) {
            message.append("• Nama: ").append(visit.getName()).append("\n");
        }
        if (visit.getAddress() != null) {
            message.append("• Alamat: ").append(visit.getAddress()).append("\n");
        }
        if (visit.getInterested() != null) {
            message.append("• Minat: ").append(visit.getInterested()).append("\n");
        }
        if (visit.getUsaha() != null) {
            message.append("• Kondisi Usaha: ").append(visit.getUsaha()).append("\n");
        }

        message.append("\nTerima kasih! Data canvasing telah tersimpan di sistem.");

        return message.toString();
    }


    private String buildSurveySuccessMessage(Visit visit) {
        StringBuilder message = new StringBuilder();
        message.append("✅ *Data Survey Berhasil Disimpan*\n\n");
        message.append("📝 *Detail Survey:*\n");

        if (visit.getName() != null) {
            message.append("• Nama: ").append(visit.getName()).append("\n");
        }
        if (visit.getPlafond() != null) {
            message.append("• Plafond: Rp ").append(CurrencyUtil.formatRupiah(visit.getPlafond())).append("\n");
        }
        if (visit.getUsaha() != null) {
            message.append("• Kondisi Usaha: ").append(visit.getUsaha()).append("\n");
        }

        message.append("\nTerima kasih! Data survey telah tersimpan di sistem.");

        return message.toString();
    }


    private Mono<Void> sendErrorMessage(String jid, VisitType visitType) {
        String visitTypeName = getVisitTypeName(visitType);
        String message = String.format(
                """
                        ❌ Maaf, terjadi kesalahan saat menyimpan data %s.
                        
                        Silakan coba lagi atau hubungi administrator jika masalah berlanjut.""",
                visitTypeName
        );

        WhatsAppRequestDTO request = WhatsAppRequestDTO.builder()
                .phone(jid)
                .message(message)
                .type(WhatsAppMessageType.TEXT)
                .build();

        return whatsappService.sendMessage(request)
                .doOnSubscribe(sub -> log.info("Sending error message for {} to {}", visitType, jid))
                .then();
    }

    private String getVisitTypeName(VisitType visitType) {
        return switch (visitType) {
            case TAGIHAN -> "tagihan";
            case MONITORING -> "monitoring";
            case CANVASING -> "canvasing";
            case SURVEY -> "survey";
        };
    }


}
