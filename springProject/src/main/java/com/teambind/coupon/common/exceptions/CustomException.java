package com.teambind.coupon.common.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 쿠폰 서비스의 커스텀 예외 클래스
 * 구체적인 예외를 위한 non-abstract 클래스
 */
@Getter
public class CustomException extends RuntimeException {

	private final ErrorCode errorCode;
	private final HttpStatus httpStatus;

	public CustomException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
		this.httpStatus = errorCode.getStatus();
	}

	public CustomException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
		this.httpStatus = errorCode.getStatus();
	}

	public CustomException(ErrorCode errorCode, String message, Throwable cause) {
		super(message, cause);
		this.errorCode = errorCode;
		this.httpStatus = errorCode.getStatus();
	}

	/**
	 * 예외 타입 반환
	 */
	public String getExceptionType() {
		return "APPLICATION";
	}
}
