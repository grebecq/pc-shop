package dev.pcshop.entity;

import dev.pcshop.dto.request.StoreInfoRequest;
import dev.pcshop.util.Strings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "store_info")
@EntityListeners(AuditingEntityListener.class)
public class StoreInfo {

    public static final short ID = 1;

    @Id
    private Short id;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String hours;

    @Column(name = "legal_name")
    private String legalName;

    private String inn;

    private String ogrn;

    @Column(name = "legal_address")
    private String legalAddress;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void apply(StoreInfoRequest form) {
        this.phone = form.phone();
        this.email = form.email();
        this.hours = form.hours();
        this.legalName = Strings.blankToNull(form.legalName());
        this.inn = Strings.blankToNull(form.inn());
        this.ogrn = Strings.blankToNull(form.ogrn());
        this.legalAddress = Strings.blankToNull(form.legalAddress());
    }

    public boolean hasSeller() {
        return legalName != null && inn != null;
    }

    public String sellerLine() {
        var sb = new StringBuilder(legalName).append(", ИНН ").append(inn);
        if (ogrn != null) {
            sb.append(", ").append(ogrnLabel()).append(' ').append(ogrn);
        }
        if (legalAddress != null) {
            sb.append(", ").append(legalAddress);
        }
        return sb.toString();
    }

    public String ogrnLabel() {
        return ogrn != null && ogrn.length() == 15 ? "ОГРНИП" : "ОГРН";
    }

    public String phoneLink() {
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 11 && digits.startsWith("8")) {
            digits = "7" + digits.substring(1);
        }
        return "tel:+" + digits;
    }
}
