package com.example.tagihan.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Margin;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handles HTML to PDF conversion using Playwright
 */
@Slf4j
@Component
public class PlaywrightPdfConverter {

    private static final String PDF_FORMAT = "A4";
    private static final String MARGIN_SIZE = "10mm";

    public byte[] convert(String htmlContent) {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = launchBrowser(playwright);
            Page page = createPage(browser, htmlContent);
            byte[] pdf = generatePdf(page);

            browser.close();
            log.info("PDF converted successfully using Playwright");

            return pdf;
        } catch (Exception e) {
            log.error("Error converting HTML to PDF with Playwright", e);
            throw new RuntimeException("Failed to convert HTML to PDF", e);
        }
    }

    private Browser launchBrowser(Playwright playwright) {
        return playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true)
        );
    }

    private Page createPage(Browser browser, String htmlContent) {
        Page page = browser.newPage();
        page.setContent(htmlContent);
        return page;
    }

    private byte[] generatePdf(Page page) {
        return page.pdf(new Page.PdfOptions()
                .setFormat(PDF_FORMAT)
                .setMargin(createMargin())
                .setPrintBackground(true));
    }

    private Margin createMargin() {
        return new Margin()
                .setTop(MARGIN_SIZE)
                .setRight(MARGIN_SIZE)
                .setBottom(MARGIN_SIZE)
                .setLeft(MARGIN_SIZE);
    }
}