package com.team.payment.controller;

import com.team.payment.dto.AccountResponse;
import com.team.payment.dto.CreateAccountRequest;
import com.team.payment.dto.DepositRequest;
import com.team.payment.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 账户REST API控制器
 */
@RestController
@RequestMapping("/api/accounts")
@CrossOrigin(origins = "*")
public class AccountController {

    @Autowired
    private AccountService accountService;

    /**
     * POST /api/accounts
     * 创建账户
     * 响应码: 201 Created
     */
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(request));
    }

    /**
     * GET /api/accounts
     * 查询全部账户
     * 响应码: 200 OK
     */
    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        return ResponseEntity.ok(accountService.getAllAccounts());
    }

    /**
     * GET /api/accounts/{id}
     * 查询账户详情（包含币种，供创建支付前查询对方币种使用）
     * 响应码: 200 OK / 404 Not Found
     */
    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable("id") Long id) {
        return ResponseEntity.ok(accountService.getAccount(id));
    }

    /**
     * GET /api/accounts/by-account-name/{accountName}
     * 按账户名查询账户
     * 响应码: 200 OK / 404 Not Found
     */
    @GetMapping("/by-account-name/{accountName}")
    public ResponseEntity<AccountResponse> getByAccountName(@PathVariable("accountName") String accountName) {
        return ResponseEntity.ok(accountService.getAccountByAccountName(accountName));
    }

    /**
     * POST /api/accounts/{id}/deposit
     * 充值
     * 响应码: 200 OK
     */
    @PostMapping("/{id}/deposit")
    public ResponseEntity<AccountResponse> deposit(@PathVariable("id") Long id,
                                                    @Valid @RequestBody DepositRequest request) {
        return ResponseEntity.ok(accountService.deposit(id, request.getAmount()));
    }
}

