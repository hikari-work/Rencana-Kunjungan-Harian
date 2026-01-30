package com.example.tagihan.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WhatsAppRequestDTO {

	private String phone;
	private String message;
	@JsonProperty("reply_message_id")
	private String replyToMessageId;

	@JsonProperty("is_forwarded")
	private boolean isForwarded;

	private String caption;
	@JsonProperty("view_once")
	@Builder.Default
	private boolean viewOnce = false;

	@JsonProperty("image_url")
	private String imageUrl;

	@JsonProperty("video_url")
	private String videoUrl;

	@JsonProperty("document_url")
	private String documentUrl;

	@Builder.Default
	private Boolean compress = false;

	private transient MultipartFile multipartFile;
	private transient Resource resource;
	private transient WhatsAppMessageType type;
}
