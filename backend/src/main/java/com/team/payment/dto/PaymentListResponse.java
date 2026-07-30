package com.team.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 支付列表响应DTO
 * 包含分页信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentListResponse {

    /** 当前页的内容 */
    private List<PaymentResponse> content;

    /** 总元素数 */
    private long totalElements;

    /** 总页数 */
    private int totalPages;

    /** 当前页数（从0开始） */
    private int currentPage;

    /** 页大小 */
    private int pageSize;
}

