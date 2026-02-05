package com.example.tagihan.service;

import com.example.tagihan.entity.Visit;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Margin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PdfService {

	private final SpringTemplateEngine templateEngine;

	public Mono<byte[]> generateLKNPdf(Flux<Visit> visitFlux, String petugasName) {
		return visitFlux
				.collectList()
				.flatMap(visits -> generatePdfFromList(visits, petugasName));
	}

	private Mono<byte[]> generatePdfFromList(List<Visit> visits, String petugasName) {
		return Mono.fromCallable(() -> {
					if (visits == null || visits.isEmpty()) {
						throw new IllegalArgumentException("Tidak ada data kunjungan");
					}

					Context context = new Context();
					context.setVariable("visits", visits);
					context.setVariable("petugasName", petugasName);

					String htmlContent = templateEngine.process("lkn-report", context);

					return convertHtmlToPdfWithPlaywright(htmlContent);
				})
				.subscribeOn(Schedulers.boundedElastic())
				.doOnSuccess(pdf -> log.info("PDF generated successfully, size: {} bytes", pdf.length))
				.doOnError(error -> log.error("Error generating PDF", error));
	}

	private byte[] convertHtmlToPdfWithPlaywright(String htmlContent) {
		try (Playwright playwright = Playwright.create()) {
			Browser browser = playwright.chromium().launch(
					new BrowserType.LaunchOptions().setHeadless(true)
			);

			Page page = browser.newPage();

			page.setContent(htmlContent);

			byte[] pdf = page.pdf(new Page.PdfOptions()
					.setFormat("A4")
					.setMargin(new Margin()
							.setTop("10mm")
							.setRight("10mm")
							.setBottom("10mm")
							.setLeft("10mm"))
					.setPrintBackground(true));

			browser.close();

			log.info("PDF generated successfully using Playwright");

			return pdf;
		} catch (Exception e) {
			log.error("Error generating PDF with Playwright", e);
			throw new RuntimeException("Failed to generate PDF", e);
		}
	}
}