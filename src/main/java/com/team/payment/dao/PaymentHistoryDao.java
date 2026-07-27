package com.team.payment.dao;

import com.team.payment.entity.PaymentHistory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付历史DAO
 * 成员C负责实现
 */
@Repository
public class PaymentHistoryDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * PaymentHistory RowMapper
     */
    private static final RowMapper<PaymentHistory> HISTORY_ROW_MAPPER = (rs, rowNum) ->
        PaymentHistory.builder()
            .id(rs.getLong("id"))
            .paymentId(rs.getLong("payment_id"))
            .fromStatus(rs.getString("from_status"))
            .toStatus(rs.getString("to_status"))
            .reason(rs.getString("reason"))
            .triggeredBy(rs.getString("triggered_by"))
            .createdAt(rs.getObject("created_at", LocalDateTime.class))
            .build();

    /**
     * 插入历史记录
     */
    @Transactional
    public PaymentHistory insert(PaymentHistory history) {
        String sql = "INSERT INTO payment_history (payment_id, from_status, to_status, reason, " +
                     "triggered_by, created_at) VALUES (?, ?, ?, ?, ?, ?)";

        jdbcTemplate.update(sql,
            history.getPaymentId(),
            history.getFromStatus(),
            history.getToStatus(),
            history.getReason(),
            history.getTriggeredBy(),
            Timestamp.valueOf(LocalDateTime.now())
        );

        return history;
    }

    /**
     * 根据支付ID查询历史记录
     */
    public List<PaymentHistory> findByPaymentId(Long paymentId) {
        String sql = "SELECT * FROM payment_history WHERE payment_id = ? ORDER BY created_at ASC";
        return jdbcTemplate.query(sql, new Object[]{paymentId}, HISTORY_ROW_MAPPER);
    }

    /**
     * 查询特定支付的最后一条历史记录
     */
    public PaymentHistory findLatestByPaymentId(Long paymentId) {
        String sql = "SELECT * FROM payment_history WHERE payment_id = ? ORDER BY created_at DESC LIMIT 1";
        List<PaymentHistory> results = jdbcTemplate.query(sql, new Object[]{paymentId}, HISTORY_ROW_MAPPER);
        return results.isEmpty() ? null : results.get(0);
    }
}

