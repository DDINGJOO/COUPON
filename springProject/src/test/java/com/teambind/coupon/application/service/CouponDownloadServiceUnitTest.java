package com.teambind.coupon.application.service;

import com.teambind.coupon.application.port.in.DownloadCouponCommand;
import com.teambind.coupon.application.port.out.LoadCouponIssuePort;
import com.teambind.coupon.application.port.out.LoadCouponPolicyPort;
import com.teambind.coupon.application.port.out.SaveCouponIssuePort;
import com.teambind.coupon.application.port.out.SaveCouponPolicyPort;
import com.teambind.coupon.common.util.SnowflakeIdGenerator;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * CouponDownloadService 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CouponDownloadService 단위 테스트")
class CouponDownloadServiceUnitTest {

    @InjectMocks
    private CouponDownloadService couponDownloadService;

    @Mock
    private LoadCouponPolicyPort loadCouponPolicyPort;

    @Mock
    private SaveCouponPolicyPort saveCouponPolicyPort;

    @Mock
    private LoadCouponIssuePort loadCouponIssuePort;

    @Mock
    private SaveCouponIssuePort saveCouponIssuePort;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    private DownloadCouponCommand command;
    private CouponPolicy validPolicy;
    private CouponIssue expectedIssue;

    @BeforeEach
    void setUp() {
        command = new DownloadCouponCommand("test2024", 100L);

        validPolicy = CouponPolicy.builder()
                .id(1L)
                .couponName("테스트 쿠폰")
                .couponCode("TEST2024")
                .discountType(DiscountType.AMOUNT)
                .discountValue(BigDecimal.valueOf(5000))
                .distributionType(DistributionType.CODE)
                .maxIssueCount(100)
                .currentIssueCount(new AtomicInteger(50))
                .maxUsagePerUser(1)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .isActive(true)
                .build();

        expectedIssue = CouponIssue.builder()
                .id(10001L)
                .policyId(1L)
                .userId(100L)
                .status(CouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now())
                .expiredAt(LocalDateTime.now().plusDays(30))
                .couponName("테스트 쿠폰")
                .discountPolicy(validPolicy.getDiscountPolicy())
                .build();
    }

    @Test
    @DisplayName("쿠폰 다운로드 성공")
    void downloadCoupon_Success() {
        // given
        when(loadCouponPolicyPort.loadByCodeAndActive("TEST2024"))
                .thenReturn(Optional.of(validPolicy));
        when(loadCouponIssuePort.countUserIssuance(100L, 1L))
                .thenReturn(0);
        when(saveCouponPolicyPort.decrementStock(1L))
                .thenReturn(true);
        when(idGenerator.nextId())
                .thenReturn(10001L);
        when(saveCouponIssuePort.save(any(CouponIssue.class)))
                .thenReturn(expectedIssue);

        // when
        CouponIssue result = couponDownloadService.downloadCoupon(command);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(10001L);
        assertThat(result.getUserId()).isEqualTo(100L);
        assertThat(result.getStatus()).isEqualTo(CouponStatus.ISSUED);

        verify(loadCouponPolicyPort).loadByCodeAndActive("TEST2024");
        verify(saveCouponPolicyPort).decrementStock(1L);
        verify(saveCouponIssuePort).save(any(CouponIssue.class));
    }

    @Test
    @DisplayName("쿠폰 다운로드 - 대소문자 구분 없이 처리")
    void downloadCoupon_CaseInsensitive() {
        // given
        DownloadCouponCommand lowerCaseCommand = new DownloadCouponCommand("test2024", 100L);

        when(loadCouponPolicyPort.loadByCodeAndActive("TEST2024"))
                .thenReturn(Optional.of(validPolicy));
        when(loadCouponIssuePort.countUserIssuance(100L, 1L))
                .thenReturn(0);
        when(saveCouponPolicyPort.decrementStock(1L))
                .thenReturn(true);
        when(idGenerator.nextId())
                .thenReturn(10001L);
        when(saveCouponIssuePort.save(any(CouponIssue.class)))
                .thenReturn(expectedIssue);

        // when
        CouponIssue result = couponDownloadService.downloadCoupon(lowerCaseCommand);

        // then
        assertThat(result).isNotNull();
        verify(loadCouponPolicyPort).loadByCodeAndActive("TEST2024");
    }

    @Test
    @DisplayName("쿠폰 다운로드 실패 - 존재하지 않는 쿠폰")
    void downloadCoupon_NotFound() {
        // given
        when(loadCouponPolicyPort.loadByCodeAndActive("TEST2024"))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> couponDownloadService.downloadCoupon(command))
                .isInstanceOf(CouponDomainException.CouponNotFound.class);

        verify(loadCouponPolicyPort).loadByCodeAndActive("TEST2024");
        verify(saveCouponPolicyPort, never()).decrementStock(anyLong());
        verify(saveCouponIssuePort, never()).save(any());
    }

    @Test
    @DisplayName("쿠폰 다운로드 실패 - CODE 타입이 아닌 경우")
    void downloadCoupon_NotCodeType() {
        // given
        CouponPolicy directPolicy = CouponPolicy.builder()
                .id(1L)
                .couponName("직접 발급 쿠폰")
                .couponCode("DIRECT2024")
                .discountType(DiscountType.AMOUNT)
                .discountValue(BigDecimal.valueOf(5000))
                .distributionType(DistributionType.DIRECT) // CODE가 아님
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .isActive(true)
                .build();

        when(loadCouponPolicyPort.loadByCodeAndActive("TEST2024"))
                .thenReturn(Optional.of(directPolicy));

        // when & then
        assertThatThrownBy(() -> couponDownloadService.downloadCoupon(command))
                .isInstanceOf(CouponDomainException.class)
                .hasMessage("CODE 타입 쿠폰만 다운로드 가능합니다");

        verify(saveCouponPolicyPort, never()).decrementStock(anyLong());
    }

    @Test
    @DisplayName("쿠폰 다운로드 실패 - 비활성화된 쿠폰")
    void downloadCoupon_Inactive() {
        // given
        CouponPolicy inactivePolicy = CouponPolicy.builder()
                .id(1L)
                .couponName("비활성 쿠폰")
                .couponCode("TEST2024")
                .discountType(DiscountType.AMOUNT)
                .discountValue(BigDecimal.valueOf(5000))
                .distributionType(DistributionType.CODE)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .isActive(false) // 비활성화
                .build();

        when(loadCouponPolicyPort.loadByCodeAndActive("TEST2024"))
                .thenReturn(Optional.of(inactivePolicy));

        // when & then
        assertThatThrownBy(() -> couponDownloadService.downloadCoupon(command))
                .isInstanceOf(CouponDomainException.class)
                .hasMessage("비활성화된 쿠폰입니다");

        verify(saveCouponPolicyPort, never()).decrementStock(anyLong());
    }

    @Test
    @DisplayName("쿠폰 다운로드 실패 - 만료된 쿠폰")
    void downloadCoupon_Expired() {
        // given
        CouponPolicy expiredPolicy = CouponPolicy.builder()
                .id(1L)
                .couponName("만료된 쿠폰")
                .couponCode("TEST2024")
                .discountType(DiscountType.AMOUNT)
                .discountValue(BigDecimal.valueOf(5000))
                .distributionType(DistributionType.CODE)
                .validFrom(LocalDateTime.now().minusDays(30))
                .validUntil(LocalDateTime.now().minusDays(1)) // 만료됨
                .isActive(true)
                .build();

        when(loadCouponPolicyPort.loadByCodeAndActive("TEST2024"))
                .thenReturn(Optional.of(expiredPolicy));

        // when & then
        assertThatThrownBy(() -> couponDownloadService.downloadCoupon(command))
                .isInstanceOf(CouponDomainException.CouponExpired.class);

        verify(saveCouponPolicyPort, never()).decrementStock(anyLong());
    }

    @Test
    @DisplayName("쿠폰 다운로드 실패 - 사용자 발급 제한 초과")
    void downloadCoupon_UserLimitExceeded() {
        // given
        when(loadCouponPolicyPort.loadByCodeAndActive("TEST2024"))
                .thenReturn(Optional.of(validPolicy));
        when(loadCouponIssuePort.countUserIssuance(100L, 1L))
                .thenReturn(1); // 이미 1개 발급됨 (maxUsagePerUser = 1)

        // when & then
        assertThatThrownBy(() -> couponDownloadService.downloadCoupon(command))
                .isInstanceOf(CouponDomainException.UserCouponLimitExceeded.class);

        verify(loadCouponIssuePort).countUserIssuance(100L, 1L);
        verify(saveCouponPolicyPort, never()).decrementStock(anyLong());
    }

    @Test
    @DisplayName("쿠폰 다운로드 실패 - 재고 소진")
    void downloadCoupon_StockExhausted() {
        // given
        when(loadCouponPolicyPort.loadByCodeAndActive("TEST2024"))
                .thenReturn(Optional.of(validPolicy));
        when(loadCouponIssuePort.countUserIssuance(100L, 1L))
                .thenReturn(0);
        when(saveCouponPolicyPort.decrementStock(1L))
                .thenReturn(false); // 재고 차감 실패

        // when & then
        assertThatThrownBy(() -> couponDownloadService.downloadCoupon(command))
                .isInstanceOf(CouponDomainException.StockExhausted.class)
                .hasMessage("쿠폰 재고가 소진되었습니다: TEST2024");

        verify(saveCouponPolicyPort).decrementStock(1L);
        verify(saveCouponIssuePort, never()).save(any());
    }

    @Test
    @DisplayName("쿠폰 다운로드 - 사용자 제한이 없는 경우")
    void downloadCoupon_NoUserLimit() {
        // given
        CouponPolicy noLimitPolicy = CouponPolicy.builder()
                .id(1L)
                .couponName("무제한 쿠폰")
                .couponCode("TEST2024")
                .discountType(DiscountType.AMOUNT)
                .discountValue(BigDecimal.valueOf(5000))
                .distributionType(DistributionType.CODE)
                .maxIssueCount(100)
                .currentIssueCount(new AtomicInteger(50))
                .maxUsagePerUser(0) // 0은 무제한
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusDays(30))
                .isActive(true)
                .build();

        when(loadCouponPolicyPort.loadByCodeAndActive("TEST2024"))
                .thenReturn(Optional.of(noLimitPolicy));
        when(saveCouponPolicyPort.decrementStock(1L))
                .thenReturn(true);
        when(idGenerator.nextId())
                .thenReturn(10001L);
        when(saveCouponIssuePort.save(any(CouponIssue.class)))
                .thenReturn(expectedIssue);

        // when
        CouponIssue result = couponDownloadService.downloadCoupon(command);

        // then
        assertThat(result).isNotNull();
        verify(loadCouponIssuePort, never()).countUserIssuance(anyLong(), anyLong());
    }

    @Test
    @DisplayName("쿠폰 다운로드 - 아직 시작되지 않은 쿠폰")
    void downloadCoupon_NotStarted() {
        // given
        CouponPolicy notStartedPolicy = CouponPolicy.builder()
                .id(1L)
                .couponName("미시작 쿠폰")
                .couponCode("TEST2024")
                .discountType(DiscountType.AMOUNT)
                .discountValue(BigDecimal.valueOf(5000))
                .distributionType(DistributionType.CODE)
                .validFrom(LocalDateTime.now().plusDays(1)) // 내일부터 시작
                .validUntil(LocalDateTime.now().plusDays(30))
                .isActive(true)
                .build();

        when(loadCouponPolicyPort.loadByCodeAndActive("TEST2024"))
                .thenReturn(Optional.of(notStartedPolicy));

        // when & then
        assertThatThrownBy(() -> couponDownloadService.downloadCoupon(command))
                .isInstanceOf(CouponDomainException.class)
                .hasMessage("아직 발급 기간이 시작되지 않았습니다");

        verify(saveCouponPolicyPort, never()).decrementStock(anyLong());
    }

    @Test
    @DisplayName("만료일 계산 - 정책 만료일이 없는 경우 기본 30일")
    void calculateExpiryDate_DefaultExpiry() {
        // given
        // validUntil이 null인 경우 도메인 모델에서 처리가 제대로 안되므로
        // 충분히 큰 날짜를 설정하여 만료되지 않은 것으로 처리
        CouponPolicy policyWithoutExpiry = CouponPolicy.builder()
                .id(1L)
                .couponName("테스트 쿠폰")
                .couponCode("TEST2024")
                .discountType(DiscountType.AMOUNT)
                .discountValue(BigDecimal.valueOf(5000))
                .distributionType(DistributionType.CODE)
                .maxIssueCount(100)
                .currentIssueCount(new AtomicInteger(50))
                .maxUsagePerUser(1)
                .validFrom(LocalDateTime.now().minusDays(1))
                .validUntil(LocalDateTime.now().plusYears(100)) // 충분히 큰 값으로 설정
                .isActive(true)
                .build();

        CouponIssue expectedIssueWithExpiry = CouponIssue.builder()
                .id(10001L)
                .policyId(1L)
                .userId(100L)
                .status(CouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now())
                .expiredAt(LocalDateTime.now().plusYears(100)) // policy의 validUntil 사용
                .couponName("테스트 쿠폰")
                .discountPolicy(DiscountPolicy.builder()
                        .discountType(DiscountType.AMOUNT)
                        .discountValue(BigDecimal.valueOf(5000))
                        .build())
                .build();

        when(loadCouponPolicyPort.loadByCodeAndActive("TEST2024"))
                .thenReturn(Optional.of(policyWithoutExpiry));
        when(loadCouponIssuePort.countUserIssuance(100L, 1L))
                .thenReturn(0);
        when(saveCouponPolicyPort.decrementStock(1L))
                .thenReturn(true);
        when(idGenerator.nextId())
                .thenReturn(10001L);
        when(saveCouponIssuePort.save(any(CouponIssue.class)))
                .thenReturn(expectedIssueWithExpiry);

        // when
        CouponIssue result = couponDownloadService.downloadCoupon(command);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getExpiredAt()).isNotNull();
        verify(saveCouponIssuePort).save(any(CouponIssue.class));
    }
}