package dev.pcshop.security;

import dev.pcshop.entity.Account;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

/** Вошедший покупатель: к стандартному пользователю Spring Security добавлены id и имя. */
@Getter
@EqualsAndHashCode(callSuper = true)
public class ShopUser extends User {

    private final long accountId;
    private final String displayName;

    public ShopUser(Account account) {
        super(account.getEmail(), account.getPasswordHash(), AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
        this.accountId = account.getId();
        this.displayName = account.getName();
    }
}
