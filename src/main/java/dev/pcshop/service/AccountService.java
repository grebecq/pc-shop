package dev.pcshop.service;

import dev.pcshop.dto.request.AddressRequest;
import dev.pcshop.dto.request.ProfileRequest;
import dev.pcshop.dto.request.RegisterRequest;
import dev.pcshop.entity.Account;
import dev.pcshop.entity.DeliveryAddress;
import dev.pcshop.exception.AccountException;
import dev.pcshop.repository.AccountRepository;
import dev.pcshop.repository.AddressRepository;
import dev.pcshop.security.ShopUser;
import dev.pcshop.util.Strings;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/** Регистрация, профиль и адреса доставки покупателя. */
@Service
@RequiredArgsConstructor
public class AccountService implements UserDetailsService {

    public static final int MAX_ADDRESSES = 10;

    private final AccountRepository accounts;
    private final AddressRepository addresses;
    private final PasswordEncoder encoder;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return accounts.findByEmail(normalize(email))
                .map(ShopUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("Неверный email или пароль"));
    }

    @Transactional
    public ShopUser register(RegisterRequest form) {
        String email = normalize(form.email());
        if (accounts.existsByEmail(email)) {
            throw new AccountException("Аккаунт с таким email уже есть. Войдите или используйте другой email.");
        }
        try {
            Account account = accounts.saveAndFlush(new Account(email, encoder.encode(form.password()), form.name().strip()));
            return new ShopUser(account);
        } catch (DataIntegrityViolationException e) {
            // Две регистрации с одним email одновременно: вторая упирается в UNIQUE
            throw new AccountException("Аккаунт с таким email уже есть. Войдите или используйте другой email.");
        }
    }

    @Transactional(readOnly = true)
    public Account get(long accountId) {
        return accounts.findById(accountId).orElseThrow(() -> new AccountException("Аккаунт не найден"));
    }

    @Transactional
    public void updateProfile(long accountId, ProfileRequest form) {
        Account account = get(accountId);
        account.setName(form.name().strip());
        account.setPhone(Strings.blankToNull(form.phone()));
    }

    /** Телефон из первого заказа запоминаем в профиле, чтобы не вводить его снова. */
    @Transactional
    public void rememberPhone(long accountId, String phone) {
        Account account = get(accountId);
        if (account.getPhone() == null && phone != null && !phone.isBlank()) {
            account.setPhone(phone.strip());
        }
    }

    @Transactional
    public void changePassword(long accountId, String current, String next) {
        Account account = get(accountId);
        if (!encoder.matches(current, account.getPasswordHash())) {
            throw new AccountException("Текущий пароль указан неверно");
        }
        if (next == null || next.length() < 8 || next.length() > 72) {
            throw new AccountException("Новый пароль — от 8 до 72 символов");
        }
        account.setPasswordHash(encoder.encode(next));
    }

    @Transactional(readOnly = true)
    public List<DeliveryAddress> addresses(long accountId) {
        return addresses.findAllByAccountIdOrderByDefaultAddressDescIdAsc(accountId);
    }

    @Transactional(readOnly = true)
    public DeliveryAddress address(long accountId, long addressId) {
        // Ищем только среди адресов этого аккаунта: чужой id даст «не найден»
        return addresses.findByIdAndAccountId(addressId, accountId)
                .orElseThrow(() -> new AccountException("Адрес не найден"));
    }

    @Transactional
    public DeliveryAddress addAddress(long accountId, AddressRequest form) {
        long count = addresses.countByAccountId(accountId);
        if (count >= MAX_ADDRESSES) {
            throw new AccountException("Можно сохранить не больше " + MAX_ADDRESSES + " адресов");
        }
        DeliveryAddress address = new DeliveryAddress(accountId);
        address.apply(form);
        // Первый адрес сразу становится основным
        if (form.wantsDefault() || count == 0) {
            addresses.clearDefault(accountId);
            address.setDefaultAddress(true);
        }
        return addresses.save(address);
    }

    @Transactional
    public void updateAddress(long accountId, long addressId, AddressRequest form) {
        DeliveryAddress address = address(accountId, addressId);
        address.apply(form);
        if (form.wantsDefault() && !address.isDefaultAddress()) {
            makeDefault(accountId, addressId);
        }
    }

    @Transactional
    public void makeDefault(long accountId, long addressId) {
        address(accountId, addressId);
        addresses.clearDefault(accountId);
        address(accountId, addressId).setDefaultAddress(true);
    }

    @Transactional
    public void deleteAddress(long accountId, long addressId) {
        DeliveryAddress address = address(accountId, addressId);
        boolean wasDefault = address.isDefaultAddress();
        addresses.delete(address);
        addresses.flush();
        // Удалили основной — основным становится самый старый из оставшихся
        if (wasDefault) {
            addresses.findAllByAccountIdOrderByDefaultAddressDescIdAsc(accountId).stream()
                    .findFirst().ifPresent(a -> a.setDefaultAddress(true));
        }
    }

    static String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
}
