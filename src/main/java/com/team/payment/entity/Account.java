package com.team.payment.entity;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账户实体类
 * 对应account表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    /** 账户ID */
    private Long id;

    /** 账户名（唯一） */
    private String accountName;

    /** 币种，如 CNY、USD、EUR */
    private String currency;

    /** 余额 */
    private BigDecimal balance;

    /** 乐观锁版本号 */
    private Long version;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}

