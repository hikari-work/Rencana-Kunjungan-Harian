package com.example.tagihan.service;

import com.example.tagihan.entity.Visit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PdfService {

	private static final String TEMPLATE_RKH = "rkh";
	private static final DateTimeFormatter DATE_FORMATTER =
			DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Asia/Jakarta"));

	private final SpringTemplateEngine templateEngine;
	private final PlaywrightPdfConverter pdfConverter;

	public Mono<byte[]> generateLKNPdf(Flux<Visit> visitFlux, String petugasName, String templateName) {
		return visitFlux
				.collectList()
				.doOnNext(visits -> log.debug("Collected {} visits for template: {}", visits.size(), templateName))
				.flatMap(visits -> generatePdf(visits, petugasName, templateName));
	}

	private Mono<byte[]> generatePdf(List<Visit> visits, String petugasName, String templateName) {
		return Mono.fromCallable(() -> {
					validateVisits(visits);

					Context context = buildContext(visits, petugasName, templateName);

					logContextDebug(context, visits.size(), templateName);

					String htmlContent = renderTemplate(templateName, context);

					logHtmlDebug(htmlContent);

					return pdfConverter.convert(htmlContent);
				})
				.subscribeOn(Schedulers.boundedElastic())
				.doOnSuccess(this::logSuccess)
				.doOnError(this::logError);
	}

	private void validateVisits(List<Visit> visits) {
		if (visits == null || visits.isEmpty()) {
			throw new IllegalArgumentException("Tidak ada data kunjungan");
		}
	}

	private Context buildContext(List<Visit> visits, String petugasName, String templateName) {
		Context context = new Context();

		if (TEMPLATE_RKH.equals(templateName)) {

			Map<String, List<Visit>> groupedVisits = groupVisitsByUserId(visits);
			context.setVariable("visitsByAo", groupedVisits);

			log.debug("RKH template: grouped {} visits into {} userId groups (pages)",
					visits.size(), groupedVisits.size());


			groupedVisits.forEach((userId, userVisits) ->
					log.debug("  - userId '{}': {} visits", userId, userVisits.size())
			);
		} else {
			context.setVariable("visits", visits);
		}

		context.setVariable("petugasName", petugasName);

		return context;
	}

	/**
	 * Group visits by userId, sort by visitDate within each group
	 * Each userId = 1 page in PDF
	 */
	private Map<String, List<Visit>> groupVisitsByUserId(List<Visit> visits) {
		return visits.stream()
				.collect(Collectors.groupingBy(
						visit -> visit.getUserId() != null ? visit.getUserId() : "Unknown User",
						LinkedHashMap::new,  // Maintain order
						Collectors.collectingAndThen(
								Collectors.toList(),
								list -> list.stream()
										.sorted(Comparator.comparing(Visit::getVisitDate))
										.collect(Collectors.toList())
						)
				));
	}

	private String renderTemplate(String templateName, Context context) {
		try {
			String html = templateEngine.process(templateName, context);

			if (html == null || html.trim().isEmpty()) {
				log.error("Template rendered empty HTML!");
				throw new RuntimeException("Template produced empty content");
			}

			return html;
		} catch (Exception e) {
			log.error("Error rendering template: {}", templateName, e);
			throw new RuntimeException("Failed to render template: " + templateName, e);
		}
	}

	private void logContextDebug(Context context, int visitCount, String templateName) {
		log.debug("=== CONTEXT DEBUG ===");
		log.debug("Template: {}", templateName);
		log.debug("Total visits in source: {}", visitCount);

		context.getVariableNames().forEach(varName -> {
			Object value = context.getVariable(varName);

			if (value instanceof Map<?, ?> map) {
				log.debug("Context var '{}': Map with {} entries", varName, map.size());


				if (TEMPLATE_RKH.equals(templateName) && "visitsByAo".equals(varName)) {
					int totalPages = map.size();
					log.debug("  => This will generate {} pages (one per userId)", totalPages);

					map.forEach((k, v) -> {
						if (v instanceof List<?> list) {
							log.debug("  - Page for userId '{}': {} visits", k, list.size());

							if (!list.isEmpty() && list.get(0) instanceof Visit firstVisit) {
								Visit lastVisit = (Visit) list.get(list.size() - 1);
								log.debug("    Date range: {} to {}",
										formatInstant(firstVisit.getVisitDate()),
										formatInstant(lastVisit.getVisitDate()));
							}
						}
					});
				}
			} else if (value instanceof List<?> list) {
				log.debug("Context var '{}': List with {} items", varName, list.size());
			} else {
				log.debug("Context var '{}': {}", varName, value);
			}
		});
	}

	private void logHtmlDebug(String htmlContent) {
		log.debug("=== HTML DEBUG ===");
		log.debug("HTML length: {} characters", htmlContent.length());

		// Preview HTML
		String preview = htmlContent.length() > 500
				? htmlContent.substring(0, 500) + "..."
				: htmlContent;
		log.debug("HTML preview: {}", preview);

		long divCount = countOccurrences(htmlContent, "<div");
		long aoSectionCount = countOccurrences(htmlContent, "class=\"ao-section\"");
		long tableCount = countOccurrences(htmlContent, "<table");
		long trCount = countOccurrences(htmlContent, "<tr");

		log.debug("HTML contains:");
		log.debug("  - {} <div> tags", divCount);
		log.debug("  - {} ao-section divs (pages)", aoSectionCount);
		log.debug("  - {} <table> tags", tableCount);
		log.debug("  - {} <tr> tags (rows)", trCount);

		if (!htmlContent.contains("<tbody>")) {
			log.warn("WARNING: HTML does not contain <tbody> tag!");
		}
		if (!htmlContent.contains("<tr")) {
			log.warn("WARNING: HTML does not contain any <tr> tags!");
		}
		if (aoSectionCount == 0) {
			log.warn("WARNING: No ao-section found! PDF might be empty.");
		}
	}

	private long countOccurrences(String text, String substring) {
		return (text.length() - text.replace(substring, "").length()) / substring.length();
	}

	private String formatInstant(Instant instant) {
		if (instant == null) {
			return "N/A";
		}
		return DATE_FORMATTER.format(instant);
	}

	private void logSuccess(byte[] pdf) {
		log.info("PDF generated successfully, size: {} bytes ({} KB)",
				pdf.length, pdf.length / 1024);

		if (pdf.length < 1000) {
			log.warn("WARNING: PDF size is suspiciously small (< 1KB), might be empty!");
		} else {
			log.info("PDF appears to have content (size looks good)");
		}
	}

	private void logError(Throwable error) {
		log.error("Error generating PDF", error);
	}
}