package com.teambind.coupon.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teambind.coupon.adapter.in.web.dto.DirectIssueRequest;
import com.teambind.coupon.adapter.in.web.dto.DirectIssueResponse;
import com.teambind.coupon.application.port.in.DirectIssueCouponUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 직접 발급 API 컨트롤러 테스트
 */
@WebMvcTest(CouponController.class)
@ActiveProfiles("test")
@DisplayName("직접 발급 API 테스트")
@org.junit.jupiter.api.Disabled("테스트 환경 설정 필요")
class DirectIssueCouponControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DirectIssueCouponUseCase directIssueCouponUseCase;

    @MockBean
    private com.teambind.coupon.application.port.in.DownloadCouponUseCase downloadCouponUseCase;
    
    @MockBean
    private com.teambind.coupon.application.port.in.ReserveCouponUseCase reserveCouponUseCase;
    
    @MockBean
    private com.teambind.coupon.application.port.out.LoadCouponPolicyPort loadCouponPolicyPort;

    private DirectIssueRequest request;
    private DirectIssueCouponUseCase.DirectIssueResult mockResult;

    @BeforeEach
    void setUp() {
        request = DirectIssueRequest.builder()
                .couponPolicyId(1L)
                .userIds(List.of(100L, 101L, 102L))
                .quantityPerUser(1)
                .issuedBy("admin@test.com")
                .reason("신규 가입 이벤트")
                .build();

        mockResult = DirectIssueCouponUseCase.DirectIssueResult.builder()
                .policyId(1L)
                .requestedCount(3)
                .successCount(3)
                .failedCount(0)
                .success(true)
                .message("직접 발급 완료")
                .failedUserIds(List.of())
                .errors(List.of())
                .build();
    }

    @Test
    @DisplayName("직접 발급 전체 성공시 201 Created 반환")
    void directIssue_FullSuccess_Returns201() throws Exception {
        // given
        when(directIssueCouponUseCase.directIssue(any()))
                .thenReturn(mockResult);

        // when & then
        mockMvc.perform(post("/api/coupons/direct-issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.successCount").value(3))
                .andExpect(jsonPath("$.failedCount").value(0));
    }

    @Test
    @DisplayName("직접 발급 부분 성공시 207 Multi-Status 반환")
    void directIssue_PartialSuccess_Returns207() throws Exception {
        // given
        DirectIssueCouponUseCase.DirectIssueResult partialResult = 
                DirectIssueCouponUseCase.DirectIssueResult.builder()
                        .policyId(1L)
                        .requestedCount(3)
                        .successCount(2)
                        .failedCount(1)
                        .success(true)
                        .message("부분 성공: 2/3")
                        .failedUserIds(List.of(102L))
                        .errors(List.of("이미 발급됨"))
                        .build();

        when(directIssueCouponUseCase.directIssue(any()))
                .thenReturn(partialResult);

        // when & then
        mockMvc.perform(post("/api/coupons/direct-issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isMultiStatus())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.failedCount").value(1));
    }

    @Test
    @DisplayName("직접 발급 전체 실패시 409 Conflict 반환")
    void directIssue_CompleteFailure_Returns409() throws Exception {
        // given
        DirectIssueCouponUseCase.DirectIssueResult failureResult = 
                DirectIssueCouponUseCase.DirectIssueResult.builder()
                        .policyId(1L)
                        .requestedCount(3)
                        .successCount(0)
                        .failedCount(3)
                        .success(false)
                        .message("재고 부족")
                        .failedUserIds(List.of(100L, 101L, 102L))
                        .errors(List.of("재고 부족"))
                        .build();

        when(directIssueCouponUseCase.directIssue(any()))
                .thenReturn(failureResult);

        // when & then
        mockMvc.perform(post("/api/coupons/direct-issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.successCount").value(0))
                .andExpect(jsonPath("$.failedCount").value(3));
    }
}
