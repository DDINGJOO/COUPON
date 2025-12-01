package com.teambind.coupon.application.port.in;

/**
 * 타임아웃된 예약 처리 UseCase
 * 일정 시간이 지난 예약 상태의 쿠폰을 다시 사용 가능 상태로 변경
 */
public interface ProcessTimeoutReservationsUseCase {

    /**
     * 타임아웃된 예약 쿠폰 처리
     *
     * @return 처리된 쿠폰 개수
     */
    int processTimeoutReservations();
}