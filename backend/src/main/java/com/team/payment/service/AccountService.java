package com.team.payment.service;

import com.team.payment.dto.AccountResponse;
import com.team.payment.dto.CreateAccountRequest;

import java.math.BigDecimal;
import java.util.List;

/**
 * 账户业务服务接口
 */
public interface AccountService {

    /** 创建账户 */
    AccountResponse createAccount(CreateAccountRequest request);

    /** 按ID查询账户 */
    AccountResponse getAccount(Long id);

    /** 按账户名查询账户 */
    AccountResponse getAccountByAccountName(String accountName);

    /** 查询全部账户 */
    List<AccountResponse> getAllAccounts();

    /** 充值 */
    AccountResponse deposit(Long id, BigDecimal amount);
}

