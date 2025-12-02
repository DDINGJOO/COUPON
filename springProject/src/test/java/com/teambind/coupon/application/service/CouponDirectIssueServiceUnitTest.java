package com.teambind.coupon.application.service;

import com.teambind.coupon.application.port.in.DirectIssueCouponCommand;
import com.teambind.coupon.application.port.in.DirectIssueCouponUseCase.DirectIssueResult;
import com.teambind.coupon.application.port.in.DirectIssueCouponUseCase.IssueFailure;
import com.teambind.coupon.application.port.out.LoadCouponIssuePort;
import com.teambind.coupon.application.port.out.LoadCouponPolicyPort;
import com.teambind.coupon.application.port.out.SaveCouponIssuePort;
import com.teambind.coupon.application.port.out.SaveCouponPolicyPort;
import com.teambind.coupon.domain.exception.CouponDomainException;
import com.teambind.coupon.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CouponDirectIssueService 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CouponDirectIssueService 단위 테스트")
class CouponDirectIssueServiceUnitTest {

    @InjectMocks
    private CouponDirectIssueService directIssueService;

    @Mock
    private LoadCouponPolicyPort loadCouponPolicyPort;

    @Mock
    private SaveCouponPolicyPort saveCouponPolicyPort;

    @Mock
    private LoadCouponIssuePort loadCouponIssuePort;

    @Mock
    private SaveCouponIssuePort saveCouponIssuePort;

    private DirectIssueCouponCommand command;
    private CouponPolicy directPolicy;

    @BeforeEach
    void setUp() {
        command = DirectIssueCouponCommand.builder()
                .couponPolicyId(10L)
                .userIds(List.of(100L, 101L, 102L))
                .quantityPerUser(1)
                .issuedBy("admin")
                .reason("이벤트 보상")
                .skipValidation(false)
                .build();

        directPolicy = CouponPolicy.builder()
                .id(10L)
                .couponName("보상 쿠폰")
                .couponCode("REWARD2024")
                .discountType(DiscountType.AMOUNT)
                .discountValue(BigDecimal.valueOf(10000))
                .distributionType(DistributionType.DIRECT)
                .maxIssueCount(100)
                .currentIssueCount(new AtomicInteger(50))
                .maxUsagePerUser(5)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .isActive(true)
                .build();
    }

    @Test
    @DisplayName("직접 발급 성공 - 전체 사용자")
    void directIssue_Success_AllUsers() {
        // given
        when(loadCouponPolicyPort.loadById(10L)).thenReturn(Optional.of(directPolicy));
        when(loadCouponIssuePort.countUserIssuance(anyLong(), eq(10L))).thenReturn(0);
        when(saveCouponPolicyPort.decrementStock(10L)).thenReturn(true);

        CouponIssue mockIssue = CouponIssue.builder()
                .id(1001L)
                .policyId(10L)
                .userId(100L)
                .status(CouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now())
                .expiredAt(LocalDateTime.now().plusDays(30))
                .build();

        when(saveCouponIssuePort.save(any(CouponIssue.class))).thenReturn(mockIssue);

        // when
        DirectIssueResult result = directIssueService.directIssue(command);

        // then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSuccessCount()).isEqualTo(3); // 3명 모두 성공
        assertThat(result.getFailedCount()).isEqualTo(0);
        assertThat(result.getIssuedCoupons()).hasSize(3);

        verify(saveCouponIssuePort, times(3)).save(any(CouponIssue.class));
        verify(saveCouponPolicyPort, times(3)).decrementStock(10L);
    }

    @Test
    @DisplayName("직접 발급 - 정책을 찾을 수 없음")
    void directIssue_PolicyNotFound() {
        // given
        when(loadCouponPolicyPort.loadById(10L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> directIssueService.directIssue(command))
                .isInstanceOf(CouponDomainException.CouponNotFound.class);

        verify(saveCouponIssuePort, never()).save(any());
        verify(saveCouponPolicyPort, never()).decrementStock(anyLong());
    }

    @Test
    @DisplayName("직접 발급 실패 - DIRECT 타입이 아님")
    void directIssue_NotDirectType() {
        // given
        CouponPolicy codePolicy = CouponPolicy.builder()
                .id(10L)
                .couponName("코드 쿠폰")
                .distributionType(DistributionType.CODE) // DIRECT가 아님
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .isActive(true)
                .build();

        when(loadCouponPolicyPort.loadById(10L)).thenReturn(Optional.of(codePolicy));

        // when & then
        assertThatThrownBy(() -> directIssueService.directIssue(command))
                .isInstanceOf(CouponDomainException.class)
                .hasMessageContaining("DIRECT 타입");

        verify(saveCouponIssuePort, never()).save(any());
    }

    @Test
    @DisplayName("직접 발급 - 재고 부족")
    void directIssue_InsufficientStock() {
        // given
        CouponPolicy lowStockPolicy = CouponPolicy.builder()
                .id(10L)
                .couponName("재고 부족 쿠폰")
                .distributionType(DistributionType.DIRECT)
                .maxIssueCount(50)
                .currentIssueCount(new AtomicInteger(49)) // 1개만 남음
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .isActive(true)
                .build();

        when(loadCouponPolicyPort.loadById(10L)).thenReturn(Optional.of(lowStockPolicy));

        // when
        DirectIssueResult result = directIssueService.directIssue(command);

        // then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getSuccessCount()).isEqualTo(0);
        assertThat(result.getMessage()).contains("재고 부족");

        verify(saveCouponIssuePort, never()).save(any());
    }

    @Test
    @DisplayName("직접 발급 - 부분 성공 (일부 사용자 이미 초과)")
    void directIssue_PartialSuccess() {
        // given
        when(loadCouponPolicyPort.loadById(10L)).thenReturn(Optional.of(directPolicy));

        // 첫 번째 사용자는 이미 한도 초과
        when(loadCouponIssuePort.countUserIssuance(100L, 10L)).thenReturn(5);
        // 나머지 사용자는 정상
        when(loadCouponIssuePort.countUserIssuance(101L, 10L)).thenReturn(0);
        when(loadCouponIssuePort.countUserIssuance(102L, 10L)).thenReturn(0);

        when(saveCouponPolicyPort.decrementStock(10L)).thenReturn(true);

        CouponIssue mockIssue = CouponIssue.builder()
                .id(1001L)
                .policyId(10L)
                .status(CouponStatus.ISSUED)
                .build();

        when(saveCouponIssuePort.save(any(CouponIssue.class))).thenReturn(mockIssue);

        // when
        DirectIssueResult result = directIssueService.directIssue(command);

        // then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSuccessCount()).isEqualTo(2); // 2명 성공
        assertThat(result.getFailedCount()).isEqualTo(1); // 1명 실패
        assertThat(result.getFailures()).hasSize(1);

        verify(saveCouponIssuePort, times(2)).save(any(CouponIssue.class));
        verify(saveCouponPolicyPort, times(2)).decrementStock(10L);
    }

    @Test
    @DisplayName("직접 발급 - 복수 수량 발급")
    void directIssue_MultipleQuantityPerUser() {
        // given
        DirectIssueCouponCommand multiCommand = DirectIssueCouponCommand.builder()
                .couponPolicyId(10L)
                .userIds(List.of(100L, 101L))
                .quantityPerUser(3) // 사용자당 3개
                .issuedBy("admin")
                .build();

        when(loadCouponPolicyPort.loadById(10L)).thenReturn(Optional.of(directPolicy));
        when(loadCouponIssuePort.countUserIssuance(anyLong(), eq(10L))).thenReturn(0);
        when(saveCouponPolicyPort.decrementStock(10L)).thenReturn(true);

        CouponIssue mockIssue = CouponIssue.builder()
                .id(1001L)
                .policyId(10L)
                .status(CouponStatus.ISSUED)
                .build();

        when(saveCouponIssuePort.save(any(CouponIssue.class))).thenReturn(mockIssue);

        // when
        DirectIssueResult result = directIssueService.directIssue(multiCommand);

        // then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSuccessCount()).isEqualTo(6); // 2명 x 3개 = 6개
        assertThat(result.getFailedCount()).isEqualTo(0);

        verify(saveCouponIssuePort, times(6)).save(any(CouponIssue.class));
        verify(saveCouponPolicyPort, times(6)).decrementStock(10L);
    }

    @Test
    @DisplayName("직접 발급 - 검증 스킵 옵션")
    void directIssue_SkipValidation() {
        // given
        DirectIssueCouponCommand skipCommand = DirectIssueCouponCommand.builder()
                .couponPolicyId(10L)
                .userIds(List.of(100L))
                .quantityPerUser(1)
                .issuedBy("admin")
                .skipValidation(true) // 검증 스킵
                .build();

        when(loadCouponPolicyPort.loadById(10L)).thenReturn(Optional.of(directPolicy));
        when(saveCouponPolicyPort.decrementStock(10L)).thenReturn(true);

        CouponIssue mockIssue = CouponIssue.builder()
                .id(1001L)
                .policyId(10L)
                .status(CouponStatus.ISSUED)
                .build();

        when(saveCouponIssuePort.save(any(CouponIssue.class))).thenReturn(mockIssue);

        // when
        DirectIssueResult result = directIssueService.directIssue(skipCommand);

        // then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSuccessCount()).isEqualTo(1);

        // 검증 스킵 시 사용자 발급 수량 체크 안함
        verify(loadCouponIssuePort, never()).countUserIssuance(anyLong(), anyLong());
    }

    @Test
    @DisplayName("직접 발급 - 비활성화된 정책")
    void directIssue_InactivePolicy() {
        // given
        CouponPolicy inactivePolicy = CouponPolicy.builder()
                .id(10L)
                .couponName("비활성 쿠폰")
                .distributionType(DistributionType.DIRECT)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .isActive(false) // 비활성
                .build();

        when(loadCouponPolicyPort.loadById(10L)).thenReturn(Optional.of(inactivePolicy));

        // when & then
        assertThatThrownBy(() -> directIssueService.directIssue(command))
                .isInstanceOf(CouponDomainException.class)
                .hasMessageContaining("비활성");

        verify(saveCouponIssuePort, never()).save(any());
    }

    @Test
    @DisplayName("직접 발급 - 재고 차감 실패 시 롤백")
    void directIssue_StockDecrementFailed_Rollback() {
        // given
        when(loadCouponPolicyPort.loadById(10L)).thenReturn(Optional.of(directPolicy));
        when(loadCouponIssuePort.countUserIssuance(anyLong(), eq(10L))).thenReturn(0);

        // 첫 번째 재고 차감만 성공
        when(saveCouponPolicyPort.decrementStock(10L))
                .thenReturn(true)
                .thenReturn(false); // 두 번째는 실패

        CouponIssue mockIssue = CouponIssue.builder()
                .id(1001L)
                .policyId(10L)
                .status(CouponStatus.ISSUED)
                .build();

        when(saveCouponIssuePort.save(any(CouponIssue.class))).thenReturn(mockIssue);

        // when & then
        assertThatThrownBy(() -> directIssueService.directIssue(command))
                .isInstanceOf(CouponDomainException.class)
                .hasMessage("재고 업데이트 실패");

        verify(saveCouponPolicyPort, atLeast(2)).decrementStock(10L);
    }

    @Test
    @DisplayName("직접 발급 - 빈 사용자 목록")
    void directIssue_EmptyUserList() {
        // given
        DirectIssueCouponCommand emptyCommand = DirectIssueCouponCommand.builder()
                .couponPolicyId(10L)
                .userIds(List.of()) // 빈 목록
                .quantityPerUser(1)
                .issuedBy("admin")
                .build();

        when(loadCouponPolicyPort.loadById(10L)).thenReturn(Optional.of(directPolicy));

        // when
        DirectIssueResult result = directIssueService.directIssue(emptyCommand);

        // then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getSuccessCount()).isEqualTo(0);
        assertThat(result.getFailedCount()).isEqualTo(0);

        verify(saveCouponIssuePort, never()).save(any());
        verify(saveCouponPolicyPort, never()).decrementStock(anyLong());
    }
}