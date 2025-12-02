package com.teambind.coupon.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teambind.coupon.application.dto.request.CouponApplyRequest;
import com.teambind.coupon.application.dto.response.CouponApplyResponse;
import com.teambind.coupon.application.port.in.ApplyCouponUseCase;
import com.teambind.coupon.domain.model.DiscountType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 쿠폰 적용 컨트롤러 테스트
 */
@WebMvcTest(CouponApplyController.class)
@DisplayName("쿠폰 적용 API 테스트")
class CouponApplyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ApplyCouponUseCase applyCouponUseCase;

    @Test
    @DisplayName("쿠폰 적용 성공")
    void applyCoupon_Success() throws Exception {
        // given
        CouponApplyRequest request = CouponApplyRequest.builder()
                .reservationId("RESV-123")
                .userId(123L)
                .couponId(1L)
                .orderAmount(BigDecimal.valueOf(100000))
                .build();

        CouponApplyResponse response = CouponApplyResponse.builder()
                .couponId("1001")
                .couponName("신규 회원 5000원 할인")
                .discountType(DiscountType.AMOUNT)
                .discountValue(BigDecimal.valueOf(5000))
                .maxDiscountAmount(null)
                .build();

        when(applyCouponUseCase.applyCoupon(any(CouponApplyRequest.class)))
                .thenReturn(response);

        // when & then
        mockMvc.perform(post("/api/coupons/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.couponId").value("1001"))
                .andExpect(jsonPath("$.couponName").value("신규 회원 5000원 할인"))
                .andExpect(jsonPath("$.discountType").value("AMOUNT"))
                .andExpect(jsonPath("$.discountValue").value(5000));

        verify(applyCouponUseCase).applyCoupon(any(CouponApplyRequest.class));
    }

    @Test
    @DisplayName("적용 가능한 쿠폰이 없을 때")
    void applyCoupon_NoCouponAvailable() throws Exception {
        // given
        CouponApplyRequest request = CouponApplyRequest.builder()
                .reservationId("RESV-123")
                .userId(123L)
                .couponId(1L)
                .orderAmount(BigDecimal.valueOf(100000))
                .build();

        when(applyCouponUseCase.applyCoupon(any(CouponApplyRequest.class)))
                .thenReturn(CouponApplyResponse.empty());

        // when & then
        mockMvc.perform(post("/api/coupons/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isNoContent());

        verify(applyCouponUseCase).applyCoupon(any(CouponApplyRequest.class));
    }

    @Test
    @DisplayName("쿠폰 락 해제 성공")
    void releaseCouponLock_Success() throws Exception {
        // given
        String reservationId = "RESV-2024-0001";

        // when & then
        mockMvc.perform(delete("/api/coupons/apply/{reservationId}", reservationId))
                .andDo(print())
                .andExpect(status().isOk());

        verify(applyCouponUseCase).releaseCouponLock(reservationId);
    }

    @Test
    @DisplayName("요청 검증 실패 - userId 누락")
    void applyCoupon_ValidationError_MissingUserId() throws Exception {
        // given
        String requestJson = """
                {
                    "reservationId": "RESV-123",
                    "couponId": 1,
                    "orderAmount": 100000
                }
                """;

        // when & then
        mockMvc.perform(post("/api/coupons/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("요청 검증 실패 - 쿠폰 ID 누락")
    void applyCoupon_ValidationError_MissingCouponId() throws Exception {
        // given
        String requestJson = """
                {
                    "reservationId": "RESV-123",
                    "userId": 123,
                    "orderAmount": 100000
                }
                """;

        // when & then
        mockMvc.perform(post("/api/coupons/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }
}