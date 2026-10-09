package dev.pcshop.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.Instant;

/** Сохранённый адрес доставки покупателя. */
@Entity
public class DeliveryAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long accountId;
    private String label;
    private String city;
    private String street;
    private String apartment;
    private String postalCode;

    @Column(name = "is_default")
    private boolean defaultAddress;

    private Instant createdAt = Instant.now();

    protected DeliveryAddress() {
    }

    DeliveryAddress(long accountId) {
        this.accountId = accountId;
    }

    void apply(AddressForm form) {
        this.label = form.label().strip();
        this.city = form.city().strip();
        this.street = form.street().strip();
        this.apartment = AddressForm.blankToNull(form.apartment());
        this.postalCode = AddressForm.blankToNull(form.postalCode());
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

    public AddressForm toForm() {
        return new AddressForm(label, city, street, apartment, postalCode, defaultAddress);
    }

    public Long getId() { return id; }
    public long getAccountId() { return accountId; }
    public String getLabel() { return label; }
    public String getCity() { return city; }
    public String getStreet() { return street; }
    public String getApartment() { return apartment; }
    public String getPostalCode() { return postalCode; }
    public boolean isDefaultAddress() { return defaultAddress; }

    void setDefaultAddress(boolean defaultAddress) { this.defaultAddress = defaultAddress; }
}
