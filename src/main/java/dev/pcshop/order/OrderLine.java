package dev.pcshop.order;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record OrderLine(long productId, @Min(1) @Max(20) int quantity) {
}
