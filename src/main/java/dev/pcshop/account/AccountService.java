package dev.pcshop.account;

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
public class AccountService implements UserDetailsService {

    static final int MAX_ADDRESSES = 10;

    private final AccountRepository accounts;
    private final AddressRepository addresses;
    private final PasswordEncoder encoder;

    AccountService(AccountRepository accounts, AddressRepository addresses, PasswordEncoder encoder) {
        this.accounts = accounts;
        this.addresses = addresses;
        this.encoder = encoder;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return accounts.findByEmail(normalize(email))
                .map(ShopUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("Неверный email или пароль"));
    }

    @Transactional
    public ShopUser register(RegisterForm form) {
        String email = normalize(form.email());
        if (accounts.existsByEmail(email)) {
            throw new AccountException("Аккаунт с таким email уже есть. Войдите или используйте другой email.");
        }
        Account account = accounts.save(new Account(email, encoder.encode(form.password()), form.name().strip()));
        return new ShopUser(account);
    }

    @Transactional(readOnly = true)
    public Account get(long accountId) {
        return accounts.findById(accountId).orElseThrow(() -> new AccountException("Аккаунт не найден"));
    }

    @Transactional
    public void updateProfile(long accountId, ProfileForm form) {
        Account account = get(accountId);
        account.setName(form.name().strip());
        account.setPhone(AddressForm.blankToNull(form.phone()));
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
    public DeliveryAddress addAddress(long accountId, AddressForm form) {
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
    public void updateAddress(long accountId, long addressId, AddressForm form) {
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
