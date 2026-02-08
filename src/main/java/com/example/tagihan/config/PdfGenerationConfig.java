package com.example.tagihan.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for PDF generation settings
 */
@Configuration
public class PdfGenerationConfig {

    @Bean
    public PdfSettings pdfSettings() {
        return PdfSettings.builder()
                .format("A4")
                .marginSize("10mm")
                .printBackground(true)
                .headless(true)
                .build();
    }
}