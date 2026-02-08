package com.example.tagihan.config;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PdfSettings {
    private String format;
    private String marginSize;
    private boolean printBackground;
    private boolean headless;
}