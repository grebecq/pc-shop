package dev.pcshop.dto.response;

import java.util.UUID;

public record OrderCreatedResponse(UUID publicId, String number, int total, String paymentUrl) {
}
