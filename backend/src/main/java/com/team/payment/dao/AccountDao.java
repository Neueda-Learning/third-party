package com.team.payment.dao;

import com.team.payment.entity.Account;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 账户DAO
 */
@Repository
public class AccountDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final RowMapper<Account> ROW_MAPPER = (rs, rowNum) -> Account.builder()
            .id(rs.getLong("id"))
            .accountName(rs.getString("account_name"))
            .currency(rs.getString("currency"))
            .balance(rs.getBigDecimal("balance"))
            .version(rs.getLong("version"))
            .createdAt(rs.getObject("created_at", LocalDateTime.class))
            .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
            .build();

    /**
     * 创建账户
     */
    @Transactional
    public Account create(Account account) {
        String sql = "INSERT INTO account (account_name, currency, balance, version, created_at, updated_at) " +
                     "VALUES (?, ?, ?, 0, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, account.getAccountName());
            ps.setString(2, account.getCurrency());
            ps.setBigDecimal(3, account.getBalance());
            ps.setTimestamp(4, Timestamp.valueOf(now));
            ps.setTimestamp(5, Timestamp.valueOf(now));
            return ps;
        }, keyHolder);
        account.setId(Objects.requireNonNull(keyHolder.getKey()).longValue());
        return account;
    }

    /**
     * 按ID查询
     */
    public Account findById(Long id) {
        List<Account> list = jdbcTemplate.query(
                "SELECT * FROM account WHERE id = ?", new Object[]{id}, ROW_MAPPER);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 按账户名查询
     */
    public Account findByAccountName(String accountName) {
        List<Account> list = jdbcTemplate.query(
                "SELECT * FROM account WHERE account_name = ?", new Object[]{accountName}, ROW_MAPPER);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 查询全部账户
     */
    public List<Account> findAll() {
        return jdbcTemplate.query("SELECT * FROM account ORDER BY id", ROW_MAPPER);
    }

    /**
     * 加行锁查询（SELECT FOR UPDATE，用于转账事务中防并发）
     */
    public Account findByIdForUpdate(Long id) {
        List<Account> list = jdbcTemplate.query(
                "SELECT * FROM account WHERE id = ? FOR UPDATE", new Object[]{id}, ROW_MAPPER);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 乐观锁更新余额
     * delta 正数=入账，负数=扣款
     * 返回影响行数，0表示版本冲突
     */
    public int updateBalance(Long id, BigDecimal delta, Long expectedVersion) {
        return jdbcTemplate.update(
                "UPDATE account SET balance = balance + ?, version = version + 1, updated_at = ? " +
                "WHERE id = ? AND version = ?",
                delta, Timestamp.valueOf(LocalDateTime.now()), id, expectedVersion);
    }

    /**
     * 充值（直接增加余额，无需乐观锁）
     */
    public void deposit(Long id, BigDecimal amount) {
        jdbcTemplate.update(
                "UPDATE account SET balance = balance + ?, version = version + 1, updated_at = ? WHERE id = ?",
                amount, Timestamp.valueOf(LocalDateTime.now()), id);
    }
}

