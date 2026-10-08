package dev.pcshop.account;

import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

/** Вошедший покупатель: к стандартному пользователю Spring Security добавлены id и имя. */
public class ShopUser extends User {

    private final long accountId;
    private final String displayName;

    ShopUser(Account account) {
        super(account.getEmail(), account.getPasswordHash(), AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
        this.accountId = account.getId();
        this.displayName = account.getName();
    }

    public long getAccountId() { return accountId; }
    public String getDisplayName() { return displayName; }
}
