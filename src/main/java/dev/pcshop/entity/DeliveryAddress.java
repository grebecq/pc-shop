package dev.pcshop.entity;

import dev.pcshop.dto.request.AddressRequest;
import dev.pcshop.util.Strings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/** Сохранённый адрес доставки покупателя. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "delivery_address")
@EntityListeners(AuditingEntityListener.class)
public class DeliveryAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private long accountId;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String street;

    private String apartment;

    @Column(name = "postal_code")
    private String postalCode;

    @Setter
    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public DeliveryAddress(long accountId) {
        this.accountId = accountId;
    }

    public void apply(AddressRequest request) {
        this.label = request.label().strip();
        this.city = request.city().strip();
        this.street = request.street().strip();
        this.apartment = Strings.blankToNull(request.apartment());
        this.postalCode = Strings.blankToNull(request.postalCode());
    }

    /** Адрес одной строкой, как он попадает в заказ: «Москва, Тверская 1, кв. 5, индекс 125009». */
    public String oneLine() {
        var sb = new StringBuilder();
        sb.append(city).append(", ").append(street);
        if (apartment != null) {
            sb.append(", кв. ").append(apartment);
        }
        // Индекс в конце и с подписью: в начале строки его путают с частью адреса
        if (postalCode != null) {
            sb.append(", индекс ").append(postalCode);
        }
        return sb.toString();
    }

    public AddressRequest toRequest() {
        return new AddressRequest(label, city, street, apartment, postalCode, defaultAddress);
    }
}
