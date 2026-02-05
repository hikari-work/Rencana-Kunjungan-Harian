package com.example.tagihan.service;

import com.example.tagihan.entity.Visit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
		return Mono.defer(() -> {
					Context context = new Context();
					context.setVariable("visits", visits);
					context.setVariable("petugasName", petugasName);

					String htmlContent = templateEngine.process("lkn-report", context);

					return convertHtmlToPdf(htmlContent);
				})
				.subscribeOn(Schedulers.boundedElastic())
				.doOnSuccess(pdf -> log.info("PDF generated successfully, size: {} bytes", pdf.length))
				.doOnError(error -> log.error("Error generating PDF", error));
	}

	private Mono<byte[]> convertHtmlToPdf(String htmlContent) {
		return Mono.fromFuture(() ->
				CompletableFuture.supplyAsync(() -> {
					Path htmlFile = null;
					Path pdfFile = null;

					try {
						htmlFile = Files.createTempFile("lkn-", ".html");
						pdfFile = Files.createTempFile("lkn-", ".pdf");

						Files.writeString(htmlFile, htmlContent);

						log.info("Converting HTML to PDF: {} -> {}", htmlFile, pdfFile);

						int exitCode = getExitCode(htmlFile, pdfFile);

						if (exitCode != 0) {
							throw new RuntimeException("wkhtmltopdf failed with exit code: " + exitCode);
						}

						byte[] pdfBytes = Files.readAllBytes(pdfFile);

						log.info("PDF conversion successful, size: {} bytes", pdfBytes.length);

						return pdfBytes;

					} catch (IOException | InterruptedException e) {
						log.error("Error during PDF conversion", e);
						throw new RuntimeException("Failed to convert HTML to PDF", e);
					} finally {
						cleanupFile(htmlFile);
						cleanupFile(pdfFile);
					}
				})
		);
	}

	private static int getExitCode(Path htmlFile, Path pdfFile) throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(
				"wkhtmltopdf",
				"--page-size", "A4",
				"--margin-top", "10mm",
				"--margin-bottom", "10mm",
				"--margin-left", "10mm",
				"--margin-right", "10mm",
				"--enable-local-file-access",
				htmlFile.toString(),
				pdfFile.toString()
		);

		pb.redirectErrorStream(true);

		Process process = pb.start();

		return process.waitFor();
	}

	private void cleanupFile(Path file) {
		if (file != null) {
			try {
				Files.deleteIfExists(file);
				log.debug("Cleaned up temp file: {}", file);
			} catch (IOException e) {
				log.warn("Failed to delete temp file: {}", file, e);
			}
		}
	}
}