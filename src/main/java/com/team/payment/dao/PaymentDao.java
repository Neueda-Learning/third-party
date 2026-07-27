package com.team.payment.dao;

import com.team.payment.entity.Payment;
import com.team.payment.entity.PaymentHistory;
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
 * 支付DAO
 * 通过JDBC Template访问数据库
 */
@Repository
public class PaymentDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Payment RowMapper
     */
    private static final RowMapper<Payment> PAYMENT_ROW_MAPPER = (rs, rowNum) ->
        Payment.builder()
            .id(rs.getLong("id"))
            .idempotencyKey(rs.getString("idempotency_key"))
            .sourceAccount(rs.getString("source_account"))
            .destinationAccount(rs.getString("destination_account"))
            .amount(rs.getBigDecimal("amount"))
            .currency(rs.getString("currency"))
            .status(rs.getString("status"))
            .errorCode(rs.getString("error_code"))
            .errorMessage(rs.getString("error_message"))
            .reference(rs.getString("reference"))
            .version(rs.getLong("version"))
            .createdAt(rs.getObject("created_at", LocalDateTime.class))
            .updatedAt(rs.getObject("updated_at", LocalDateTime.class))
            .build();

    /**
     * 创建支付记录
     */
    @Transactional
    public Payment create(Payment payment) {
        String sql = "INSERT INTO payments (idempotency_key, source_account, destination_account, " +
                     "amount, currency, status, reference, version, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, payment.getIdempotencyKey());
            ps.setString(2, payment.getSourceAccount());
            ps.setString(3, payment.getDestinationAccount());
            ps.setBigDecimal(4, payment.getAmount());
            ps.setString(5, payment.getCurrency());
            ps.setString(6, payment.getStatus());
            ps.setString(7, payment.getReference());
            ps.setLong(8, 0L);
            ps.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));
            ps.setTimestamp(10, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, keyHolder);

        payment.setId(Objects.requireNonNull(keyHolder.getKey()).longValue());
        return payment;
    }

    /**
     * 根据ID查询支付
     */
    public Payment findById(Long id) {
        String sql = "SELECT * FROM payments WHERE id = ?";
        List<Payment> results = jdbcTemplate.query(sql, new Object[]{id}, PAYMENT_ROW_MAPPER);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 根据幂等键查询支付
     */
    public Payment findByIdempotencyKey(String idempotencyKey) {
        String sql = "SELECT * FROM payments WHERE idempotency_key = ?";
        List<Payment> results = jdbcTemplate.query(sql, new Object[]{idempotencyKey}, PAYMENT_ROW_MAPPER);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 分页查询支付（可选按状态筛选）
     */
    public List<Payment> findPaginated(int page, int size, String status) {
        String sql = "SELECT * FROM payments";
        Object[] params;

        if (status != null && !status.isEmpty()) {
            sql += " WHERE status = ?";
            params = new Object[]{status};
        } else {
            params = new Object[]{};
        }

        sql += " ORDER BY updated_at DESC LIMIT ? OFFSET ?";

        if (status != null && !status.isEmpty()) {
            Object[] newParams = new Object[3];
            newParams[0] = status;
            newParams[1] = size;
            newParams[2] = page * size;
            params = newParams;
        } else {
            params = new Object[]{size, page * size};
        }

        return jdbcTemplate.query(sql, params, PAYMENT_ROW_MAPPER);
    }

    /**
     * 统计支付记录数（可选按状态筛选）
     */
    public long countByStatus(String status) {
        String sql = "SELECT COUNT(*) FROM payments";
        Object[] params;

        if (status != null && !status.isEmpty()) {
            sql += " WHERE status = ?";
            params = new Object[]{status};
        } else {
            params = new Object[]{};
        }

        Long count = jdbcTemplate.queryForObject(sql, params, Long.class);
        return count != null ? count : 0L;
    }

    /**
     * 更新支付状态
     */
    @Transactional
    public int updateStatus(Long id, String newStatus, String errorCode, String errorMessage) {
        String sql = "UPDATE payments SET status = ?, error_code = ?, error_message = ?, " +
                     "version = version + 1, updated_at = ? WHERE id = ?";

        return jdbcTemplate.update(sql,
            newStatus,
            errorCode,
            errorMessage,
            Timestamp.valueOf(LocalDateTime.now()),
            id
        );
    }

    /**
     * 查询超时的支付（满N分钟未更新）
     */
    public List<Payment> findTimeoutPayments(String status, int timeoutMinutes) {
        String sql = "SELECT * FROM payments WHERE status = ? AND " +
                     "updated_at < DATE_SUB(NOW(), INTERVAL ? MINUTE)";
        return jdbcTemplate.query(sql, new Object[]{status, timeoutMinutes}, PAYMENT_ROW_MAPPER);
    }

    /**
     * 查询所有未完成的支付
     */
    public List<Payment> findUnfinishedPayments() {
        String sql = "SELECT * FROM payments WHERE status NOT IN ('COMPLETED', 'FAILED') " +
                     "ORDER BY updated_at ASC";
        return jdbcTemplate.query(sql, PAYMENT_ROW_MAPPER);
    }
}

