package com.teambind.coupon.domain.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CouponIssue 도메인 모델 단위 테스트
 */
@DisplayName("CouponIssue 도메인 모델 테스트")
class CouponIssueTest {

    private CouponIssue couponIssue;
    private DiscountPolicy discountPolicy;

    @BeforeEach
    void setUp() {
        discountPolicy = DiscountPolicy.builder()
                .discountType(DiscountType.AMOUNT)
                .discountValue(new BigDecimal("5000"))
                .minOrderAmount(new BigDecimal("30000"))
                .build();

        couponIssue = CouponIssue.builder()
                .id(1L)
                .policyId(100L)
                .userId(1000L)
                .status(CouponStatus.ISSUED)
                .issuedAt(LocalDateTime.now())
                .couponName("테스트 쿠폰")
                .discountPolicy(discountPolicy)
                .build();
    }

    @Nested
    @DisplayName("쿠폰 예약 처리")
    class ReservationTest {

        @Test
        @DisplayName("발급된 쿠폰 예약 성공")
        void reserveIssuedCoupon() {
            // given
            String reservationId = UUID.randomUUID().toString();

            // when
            boolean result = couponIssue.reserve(reservationId);

            // then
            assertThat(result).isTrue();
            assertThat(couponIssue.getStatus()).isEqualTo(CouponStatus.RESERVED);
            assertThat(couponIssue.getReservationId()).isEqualTo(reservationId);
            assertThat(couponIssue.getReservedAt()).isNotNull();
        }

        @Test
        @DisplayName("이미 예약된 쿠폰 재예약 실패")
        void cannotReserveAlreadyReserved() {
            // given
            String firstReservation = "RESERVATION-001";
            couponIssue.reserve(firstReservation);

            // when
            String secondReservation = "RESERVATION-002";
            boolean result = couponIssue.reserve(secondReservation);

            // then
            assertThat(result).isFalse();
            assertThat(couponIssue.getReservationId()).isEqualTo(firstReservation); // 첫 번째 예약 유지
        }

        @Test
        @DisplayName("사용된 쿠폰 예약 불가")
        void cannotReserveUsedCoupon() {
            // given
            couponIssue.use("ORDER-001", new BigDecimal("5000"));

            // when
            boolean result = couponIssue.reserve("RESERVATION-001");

            // then
            assertThat(result).isFalse();
            assertThat(couponIssue.getStatus()).isEqualTo(CouponStatus.USED);
        }

        @Test
        @DisplayName("만료된 쿠폰 예약 불가")
        void cannotReserveExpiredCoupon() {
            // given
            couponIssue.expire();

            // when
            boolean result = couponIssue.reserve("RESERVATION-001");

            // then
            assertThat(result).isFalse();
            assertThat(couponIssue.getStatus()).isEqualTo(CouponStatus.EXPIRED);
        }

        @Test
        @DisplayName("취소된 쿠폰 예약 불가")
        void cannotReserveCancelledCoupon() {
            // given
            couponIssue.cancel();

            // when
            boolean result = couponIssue.reserve("RESERVATION-001");

            // then
            assertThat(result).isFalse();
            assertThat(couponIssue.getStatus()).isEqualTo(CouponStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("쿠폰 사용 처리")
    class UseTest {

        @Test
        @DisplayName("예약된 쿠폰 사용 성공")
        void useReservedCoupon() {
            // given
            String reservationId = "RESERVATION-001";
            couponIssue.reserve(reservationId);

            // when
            boolean result = couponIssue.use("ORDER-001", new BigDecimal("3000"));

            // then
            assertThat(result).isTrue();
            assertThat(couponIssue.getStatus()).isEqualTo(CouponStatus.USED);
            assertThat(couponIssue.getOrderId()).isEqualTo("ORDER-001");
            assertThat(couponIssue.getActualDiscountAmount()).isEqualTo(new BigDecimal("3000"));
            assertThat(couponIssue.getUsedAt()).isNotNull();
        }

        @Test
        @DisplayName("발급 상태에서 직접 사용 (예약 없이)")
        void useIssuedCouponDirectly() {
            // when
            boolean result = couponIssue.use("ORDER-002", new BigDecimal("5000"));

            // then
            assertThat(result).isTrue();
            assertThat(couponIssue.getStatus()).isEqualTo(CouponStatus.USED);
        }

        @Test
        @DisplayName("이미 사용된 쿠폰 재사용 불가")
        void cannotReuseUsedCoupon() {
            // given
            couponIssue.use("ORDER-001", new BigDecimal("5000"));

            // when
            boolean result = couponIssue.use("ORDER-002", new BigDecimal("3000"));

            // then
            assertThat(result).isFalse();
            assertThat(couponIssue.getOrderId()).isEqualTo("ORDER-001"); // 첫 번째 주문 유지
        }

        @Test
        @DisplayName("만료된 쿠폰 사용 불가")
        void cannotUseExpiredCoupon() {
            // given
            couponIssue.expire();

            // when
            boolean result = couponIssue.use("ORDER-001", new BigDecimal("5000"));

            // then
            assertThat(result).isFalse();
            assertThat(couponIssue.getStatus()).isEqualTo(CouponStatus.EXPIRED);
        }
    }

    @Nested
    @DisplayName("쿠폰 예약 해제")
    class ReleaseTest {

        @Test
        @DisplayName("예약된 쿠폰 해제 성공")
        void releaseReservedCoupon() {
            // given
            couponIssue.reserve("RESERVATION-001");

            // when
            boolean result = couponIssue.release();

            // then
            assertThat(result).isTrue();
            assertThat(couponIssue.getStatus()).isEqualTo(CouponStatus.ISSUED);
            assertThat(couponIssue.getReservationId()).isNull();
            assertThat(couponIssue.getReservedAt()).isNull();
        }

        @Test
        @DisplayName("예약되지 않은 쿠폰 해제 시도")
        void releaseNonReservedCoupon() {
            // when
            boolean result = couponIssue.release();

            // then
            assertThat(result).isFalse();
            assertThat(couponIssue.getStatus()).isEqualTo(CouponStatus.ISSUED);
        }

        @Test
        @DisplayName("사용된 쿠폰 해제 불가")
        void cannotReleaseUsedCoupon() {
            // given
            couponIssue.use("ORDER-001", new BigDecimal("5000"));

            // when
            boolean result = couponIssue.release();

            // then
            assertThat(result).isFalse();
            assertThat(couponIssue.getStatus()).isEqualTo(CouponStatus.USED);
        }
    }

    @Nested
    @DisplayName("쿠폰 상태 전이")
    class StatusTransition {

        @Test
        @DisplayName("쿠폰 만료 처리")
        void expireCoupon() {
            // when
            couponIssue.expire();

            // then
            assertThat(couponIssue.getStatus()).isEqualTo(CouponStatus.EXPIRED);
            assertThat(couponIssue.getExpiredAt()).isNotNull();
        }

        @Test
        @DisplayName("쿠폰 취소 처리")
        void cancelCoupon() {
            // when
            couponIssue.cancel();

            // then
            assertThat(couponIssue.getStatus()).isEqualTo(CouponStatus.CANCELLED);
        }

        @Test
        @DisplayName("사용 후 취소 처리")
        void cancelAfterUse() {
            // given
            couponIssue.use("ORDER-001", new BigDecimal("5000"));

            // when
            couponIssue.cancel();

            // then
            assertThat(couponIssue.getStatus()).isEqualTo(CouponStatus.CANCELLED);
            assertThat(couponIssue.getOrderId()).isEqualTo("ORDER-001"); // 주문 정보 유지
            assertThat(couponIssue.getUsedAt()).isNotNull(); // 사용 시간 유지
        }
    }

    @Nested
    @DisplayName("쿠폰 상태별 가능 여부 확인")
    class StatusCheck {

        @ParameterizedTest
        @EnumSource(CouponStatus.class)
        @DisplayName("상태별 예약 가능 여부")
        void checkReservableByStatus(CouponStatus status) {
            // given
            CouponIssue coupon = CouponIssue.builder()
                    .status(status)
                    .build();

            // then
            boolean expectedReservable = (status == CouponStatus.ISSUED);
            assertThat(coupon.getStatus().isReservable()).isEqualTo(expectedReservable);
        }

        @ParameterizedTest
        @EnumSource(CouponStatus.class)
        @DisplayName("상태별 사용 가능 여부")
        void checkUsableByStatus(CouponStatus status) {
            // given
            CouponIssue coupon = CouponIssue.builder()
                    .status(status)
                    .build();

            // then
            boolean expectedUsable = (status == CouponStatus.ISSUED || status == CouponStatus.RESERVED);
            assertThat(coupon.getStatus().isUsable()).isEqualTo(expectedUsable);
        }
    }

    @Nested
    @DisplayName("예약 타임아웃 처리")
    class ReservationTimeout {

        @Test
        @DisplayName("타임아웃 시간 경과 확인")
        void checkReservationTimeout() {
            // given
            couponIssue.reserve("RESERVATION-001");

            // 30분 전에 예약된 것으로 설정
            CouponIssue timeoutCoupon = CouponIssue.builder()
                    .id(2L)
                    .policyId(100L)
                    .userId(1000L)
                    .status(CouponStatus.RESERVED)
                    .reservationId("RESERVATION-002")
                    .reservedAt(LocalDateTime.now().minusMinutes(31))
                    .build();

            // when
            boolean recentlyReserved = couponIssue.isReservationTimeout(30);
            boolean timeoutReserved = timeoutCoupon.isReservationTimeout(30);

            // then
            assertThat(recentlyReserved).isFalse();
            assertThat(timeoutReserved).isTrue();
        }

        @Test
        @DisplayName("예약되지 않은 쿠폰의 타임아웃 확인")
        void checkTimeoutForNonReserved() {
            // when
            boolean result = couponIssue.isReservationTimeout(30);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("할인 정보 관리")
    class DiscountInfo {

        @Test
        @DisplayName("실제 할인 금액 설정")
        void setActualDiscountAmount() {
            // given
            BigDecimal discountAmount = new BigDecimal("4500");

            // when
            couponIssue.use("ORDER-001", discountAmount);

            // then
            assertThat(couponIssue.getActualDiscountAmount()).isEqualTo(discountAmount);
        }

        @Test
        @DisplayName("할인 정책 정보 유지")
        void maintainDiscountPolicy() {
            // then
            assertThat(couponIssue.getDiscountPolicy()).isNotNull();
            assertThat(couponIssue.getDiscountPolicy().getDiscountType()).isEqualTo(DiscountType.AMOUNT);
            assertThat(couponIssue.getDiscountPolicy().getDiscountValue()).isEqualTo(new BigDecimal("5000"));
        }
    }

    @Nested
    @DisplayName("빌더 패턴 검증")
    class BuilderValidation {

        @Test
        @DisplayName("필수 필드만으로 생성")
        void buildWithRequiredFieldsOnly() {
            // when
            CouponIssue minimalCoupon = CouponIssue.builder()
                    .policyId(100L)
                    .userId(1000L)
                    .status(CouponStatus.ISSUED)
                    .issuedAt(LocalDateTime.now())
                    .couponName("최소 쿠폰")
                    .discountPolicy(discountPolicy)
                    .build();

            // then
            assertThat(minimalCoupon).isNotNull();
            assertThat(minimalCoupon.getPolicyId()).isEqualTo(100L);
            assertThat(minimalCoupon.getUserId()).isEqualTo(1000L);
            assertThat(minimalCoupon.getStatus()).isEqualTo(CouponStatus.ISSUED);
        }

        @Test
        @DisplayName("모든 필드로 생성")
        void buildWithAllFields() {
            // when
            LocalDateTime now = LocalDateTime.now();
            CouponIssue fullCoupon = CouponIssue.builder()
                    .id(1L)
                    .policyId(100L)
                    .userId(1000L)
                    .status(CouponStatus.USED)
                    .reservationId("RES-001")
                    .orderId("ORD-001")
                    .issuedAt(now.minusDays(1))
                    .reservedAt(now.minusHours(1))
                    .usedAt(now)
                    .expiredAt(null)
                    .actualDiscountAmount(new BigDecimal("3000"))
                    .couponName("전체 필드 쿠폰")
                    .discountPolicy(discountPolicy)
                    .build();

            // then
            assertThat(fullCoupon.getId()).isEqualTo(1L);
            assertThat(fullCoupon.getReservationId()).isEqualTo("RES-001");
            assertThat(fullCoupon.getOrderId()).isEqualTo("ORD-001");
            assertThat(fullCoupon.getActualDiscountAmount()).isEqualTo(new BigDecimal("3000"));
        }
    }
}