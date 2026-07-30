package com.team.payment.service;

import com.team.payment.dao.AccountDao;
import com.team.payment.dto.AccountResponse;
import com.team.payment.dto.CreateAccountRequest;
import com.team.payment.entity.Account;
import com.team.payment.exception.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 账户业务服务实现
 */
@Service
public class AccountServiceImpl implements AccountService {

    @Autowired
    private AccountDao accountDao;

    private static final Set<String> ISO_4217 = Currency.getAvailableCurrencies()
            .stream().map(Currency::getCurrencyCode).collect(Collectors.toUnmodifiableSet());

    @Override
    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        String currency = request.getCurrency().trim().toUpperCase();
        if (!ISO_4217.contains(currency)) {
            throw new ValidationException("INVALID_CURRENCY", "不支持的币种: " + currency);
        }
        if (accountDao.findByAccountName(request.getAccountName().trim()) != null) {
            throw new ValidationException("DUPLICATE_ACCOUNT_NAME", "账户名已存在: " + request.getAccountName());
        }
        LocalDateTime now = LocalDateTime.now();
        Account account = accountDao.create(Account.builder()
                .accountName(request.getAccountName().trim())
                .currency(currency)
                .balance(request.getInitialBalance() != null ? request.getInitialBalance() : BigDecimal.ZERO)
                .createdAt(now)
                .updatedAt(now)
                .build());
        return AccountResponse.fromEntity(account);
    }

    @Override
    public AccountResponse getAccount(Long id) {
        Account account = accountDao.findById(id);
        if (account == null) {
            throw new ValidationException("ACCOUNT_NOT_FOUND", "账户不存在: " + id);
        }
        return AccountResponse.fromEntity(account);
    }

    @Override
    public AccountResponse getAccountByAccountName(String accountName) {
        Account account = accountDao.findByAccountName(accountName);
        if (account == null) {
            throw new ValidationException("ACCOUNT_NOT_FOUND", "账户不存在: " + accountName);
        }
        return AccountResponse.fromEntity(account);
    }

    @Override
    public List<AccountResponse> getAllAccounts() {
        return accountDao.findAll().stream()
                .map(AccountResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AccountResponse deposit(Long id, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("INVALID_AMOUNT", "充值金额必须大于0");
        }
        Account account = accountDao.findById(id);
        if (account == null) {
            throw new ValidationException("ACCOUNT_NOT_FOUND", "账户不存在: " + id);
        }
        accountDao.deposit(id, amount);
        return AccountResponse.fromEntity(accountDao.findById(id));
    }
}

