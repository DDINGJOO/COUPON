package com.teambind.coupon.application.service;

import com.teambind.coupon.application.port.in.UpdateCouponPolicyRemainingQuantityCommand;
import com.teambind.coupon.application.port.out.LoadCouponPolicyPort;
import com.teambind.coupon.application.port.out.SaveCouponPolicyPort;
import com.teambind.coupon.domain.exception.CouponDomainException;
import com.teambind.coupon.domain.model.CouponPolicy;
import com.teambind.coupon.domain.model.DiscountType;
import com.teambind.coupon.domain.model.DistributionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * CouponPolicyManagementService 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CouponPolicyManagementService 테스트")
class CouponPolicyManagementServiceTest {

    @Mock
    private LoadCouponPolicyPort loadCouponPolicyPort;

    @Mock
    private SaveCouponPolicyPort saveCouponPolicyPort;

    @InjectMocks
    private CouponPolicyManagementService service;

    private CouponPolicy policy;
    private UpdateCouponPolicyRemainingQuantityCommand command;

    @BeforeEach
    void setUp() {
        policy = CouponPolicy.builder()
                .id(1L)
                .couponName("테스트 쿠폰")
                .distributionType(DistributionType.CODE)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .maxIssueCount(100)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        policy.setCurrentIssueCount(new AtomicInteger(30));
    }

    @Nested
    @DisplayName("남은 발급 수량 수정")
    class UpdateRemainingQuantity {

        @Test
        @DisplayName("정상적인 수량 수정 성공")
        void updateSuccess() {
            // given
            command = UpdateCouponPolicyRemainingQuantityCommand.builder()
                    .couponPolicyId(1L)
                    .newMaxIssueCount(200)
                    .modifiedBy(100L)
                    .reason("재고 추가")
                    .build();

            when(loadCouponPolicyPort.loadById(1L)).thenReturn(Optional.of(policy));

            // when
            CouponPolicyManagementService.UpdateResult result = service.updateRemainingQuantity(command);

            // then
            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getCouponPolicyId()).isEqualTo(1L);
            assertThat(result.getPreviousMaxIssueCount()).isEqualTo(100);
            assertThat(result.getNewMaxIssueCount()).isEqualTo(200);
            assertThat(result.getCurrentIssuedCount()).isEqualTo(30);
            assertThat(result.getMessage()).contains("성공");

            verify(loadCouponPolicyPort).loadById(1L);
            verify(saveCouponPolicyPort).save(policy);
        }

        @Test
        @DisplayName("무제한으로 변경")
        void updateToUnlimited() {
            // given
            command = UpdateCouponPolicyRemainingQuantityCommand.builder()
                    .couponPolicyId(1L)
                    .newMaxIssueCount(null) // 무제한
                    .modifiedBy(100L)
                    .reason("무제한 변경")
                    .build();

            when(loadCouponPolicyPort.loadById(1L)).thenReturn(Optional.of(policy));

            // when
            CouponPolicyManagementService.UpdateResult result = service.updateRemainingQuantity(command);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getNewMaxIssueCount()).isNull();
            assertThat(policy.getMaxIssueCount()).isNull();

            verify(saveCouponPolicyPort).save(policy);
        }

        @Test
        @DisplayName("존재하지 않는 쿠폰 정책 수정 시도")
        void updateNotExistingPolicy() {
            // given
            command = UpdateCouponPolicyRemainingQuantityCommand.builder()
                    .couponPolicyId(999L)
                    .newMaxIssueCount(100)
                    .modifiedBy(100L)
                    .build();

            when(loadCouponPolicyPort.loadById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> service.updateRemainingQuantity(command))
                    .isInstanceOf(CouponDomainException.CouponNotFound.class);

            verify(saveCouponPolicyPort, never()).save(any());
        }

        @Test
        @DisplayName("현재 발급량보다 적게 설정 시도")
        void updateLessThanCurrentIssued() {
            // given
            command = UpdateCouponPolicyRemainingQuantityCommand.builder()
                    .couponPolicyId(1L)
                    .newMaxIssueCount(20) // 현재 발급량(30)보다 적음
                    .modifiedBy(100L)
                    .build();

            when(loadCouponPolicyPort.loadById(1L)).thenReturn(Optional.of(policy));

            // when
            CouponPolicyManagementService.UpdateResult result = service.updateRemainingQuantity(command);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("현재 발급된 수량");

            verify(saveCouponPolicyPort, never()).save(any());
        }

        @Test
        @DisplayName("만료된 쿠폰 정책 수정 시도")
        void updateExpiredPolicy() {
            // given
            CouponPolicy expiredPolicy = CouponPolicy.builder()
                    .id(2L)
                    .couponName("만료된 쿠폰")
                    .validFrom(LocalDateTime.now().minusDays(10))
                    .validUntil(LocalDateTime.now().minusDays(1))
                    .maxIssueCount(100)
                    .isActive(true)
                    .build();
            expiredPolicy.setCurrentIssueCount(new AtomicInteger(50));

            command = UpdateCouponPolicyRemainingQuantityCommand.builder()
                    .couponPolicyId(2L)
                    .newMaxIssueCount(200)
                    .modifiedBy(100L)
                    .build();

            when(loadCouponPolicyPort.loadById(2L)).thenReturn(Optional.of(expiredPolicy));

            // when
            CouponPolicyManagementService.UpdateResult result = service.updateRemainingQuantity(command);

            // then
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getMessage()).contains("만료");

            verify(saveCouponPolicyPort, never()).save(any());
        }

        @Test
        @DisplayName("0으로 설정 (발급 중단)")
        void updateToZero() {
            // given
            policy.setCurrentIssueCount(new AtomicInteger(0)); // 발급된 것 없음

            command = UpdateCouponPolicyRemainingQuantityCommand.builder()
                    .couponPolicyId(1L)
                    .newMaxIssueCount(0)
                    .modifiedBy(100L)
                    .reason("발급 중단")
                    .build();

            when(loadCouponPolicyPort.loadById(1L)).thenReturn(Optional.of(policy));

            // when
            CouponPolicyManagementService.UpdateResult result = service.updateRemainingQuantity(command);

            // then
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getNewMaxIssueCount()).isEqualTo(0);
            assertThat(policy.getMaxIssueCount()).isEqualTo(0);

            verify(saveCouponPolicyPort).save(policy);
        }
    }
}