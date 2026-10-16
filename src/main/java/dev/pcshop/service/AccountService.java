package dev.pcshop.service;

import dev.pcshop.dto.request.AddressRequest;
import dev.pcshop.dto.request.ProfileRequest;
import dev.pcshop.dto.request.RegisterRequest;
import dev.pcshop.entity.Account;
import dev.pcshop.entity.DeliveryAddress;
import dev.pcshop.exception.BadRequestException;
import dev.pcshop.exception.DuplicateResourceException;
import dev.pcshop.exception.ResourceNotFoundException;
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

@Service
@RequiredArgsConstructor
public class AccountService implements UserDetailsService {

    public static final int MAX_ADDRESSES = 10;
    private static final String EMAIL_TAKEN = "Аккаунт с таким email уже есть. Войдите или используйте другой email.";

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
            throw new DuplicateResourceException(EMAIL_TAKEN);
        }
        try {
            Account account = accounts.saveAndFlush(new Account(email, encoder.encode(form.password()), form.name()));
            return new ShopUser(account);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateResourceException(EMAIL_TAKEN);
        }
    }

    @Transactional(readOnly = true)
    public Account get(long accountId) {
        return accounts.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("Аккаунт не найден"));
    }

    @Transactional
    public void updateProfile(long accountId, ProfileRequest form) {
        Account account = get(accountId);
        account.setName(form.name());
        account.setPhone(Strings.blankToNull(form.phone()));
    }

    @Transactional
    public void rememberPhone(long accountId, String phone) {
        Account account = get(accountId);
        if (account.getPhone() == null && phone != null && !phone.isBlank()) {
            account.setPhone(phone.strip());
        }
    }

    @Transactional(readOnly = true)
    public List<DeliveryAddress> addresses(long accountId) {
        return addresses.findAllByAccountIdOrderByDefaultAddressDescIdAsc(accountId);
    }

    @Transactional(readOnly = true)
    public DeliveryAddress address(long accountId, long addressId) {
        return addresses.findByIdAndAccountId(addressId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Адрес не найден"));
    }

    @Transactional
    public DeliveryAddress addAddress(long accountId, AddressRequest form) {
        long count = addresses.countByAccountId(accountId);
        if (count >= MAX_ADDRESSES) {
            throw new BadRequestException("Можно сохранить не больше " + MAX_ADDRESSES + " адресов");
        }
        DeliveryAddress address = new DeliveryAddress(accountId);
        address.apply(form);
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
        if (wasDefault) {
            addresses.findAllByAccountIdOrderByDefaultAddressDescIdAsc(accountId).stream()
                    .findFirst().ifPresent(a -> a.setDefaultAddress(true));
        }
    }

    static String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
}
