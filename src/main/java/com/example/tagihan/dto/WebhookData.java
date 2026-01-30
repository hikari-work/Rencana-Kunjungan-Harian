package com.example.tagihan.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebhookData {
    @JsonProperty("chat_id")
    private String chatId;

    private String from;

    @JsonProperty("from_lid")
    private String fromLid;

    @JsonProperty("from_name")
    private String fromName;

    private String id;

    private String timestamp;

    private String body;

    @JsonProperty("replied_to_id")
    private String repliedToId;

    private ImagePayload image;

    public boolean isImage() {
        return image != null;
    }

    public boolean isText() {
        return body != null;
    }
}