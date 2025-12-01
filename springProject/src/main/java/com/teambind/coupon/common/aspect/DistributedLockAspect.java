package com.teambind.coupon.common.aspect;

import com.teambind.coupon.adapter.out.redis.DistributedLockService;
import com.teambind.coupon.common.annotation.DistributedLock;
import com.teambind.coupon.common.exceptions.CustomException;
import com.teambind.coupon.common.exceptions.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * DistributedLock 어노테이션 처리를 위한 AOP Aspect
 * 메서드 실행 전후로 분산 락을 획득하고 해제
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DistributedLockAspect {

    private final DistributedLockService distributedLockService;
    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(distributedLock)")
    public Object handleDistributedLock(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        // 락 키 생성
        String lockKey = generateLockKey(joinPoint, distributedLock);

        log.debug("분산 락 처리 시작 - method: {}, lockKey: {}",
                joinPoint.getSignature().toShortString(), lockKey);

        // 락을 사용하여 작업 실행
        try {
            return distributedLockService.executeWithLock(
                    lockKey,
                    distributedLock.waitTime(),
                    distributedLock.leaseTime(),
                    distributedLock.timeUnit(),
                    () -> {
                        try {
                            return joinPoint.proceed();
                        } catch (Throwable throwable) {
                            throw new RuntimeException(throwable);
                        }
                    }
            );
        } catch (CustomException e) {
            // 이미 CustomException인 경우 그대로 전달
            throw e;
        } catch (RuntimeException e) {
            // RuntimeException을 풀어서 원래 예외 확인
            Throwable cause = e.getCause();
            if (cause != null) {
                throw cause;
            }
            throw e;
        } catch (Exception e) {
            log.error("분산 락 처리 중 예외 발생 - method: {}, lockKey: {}",
                    joinPoint.getSignature().toShortString(), lockKey, e);

            if (distributedLock.throwExceptionOnFail()) {
                throw new CustomException(ErrorCode.LOCK_ACQUISITION_FAILED,
                        String.format("분산 락 획득 실패: %s", lockKey));
            }
            return null;
        }
    }

    /**
     * SpEL 표현식을 평가하여 락 키 생성
     *
     * @param joinPoint       조인 포인트
     * @param distributedLock 어노테이션 정보
     * @return 생성된 락 키
     */
    private String generateLockKey(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();

        // SpEL 표현식 평가를 위한 컨텍스트 생성
        EvaluationContext context = new StandardEvaluationContext();

        // 메서드 파라미터를 컨텍스트에 추가
        String[] paramNames = signature.getParameterNames();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        // SpEL 표현식 평가
        Expression expression = parser.parseExpression(distributedLock.key());
        Object keyValue = expression.getValue(context);

        // 최종 락 키 생성
        String lockKey = distributedLock.prefix() + ":" + keyValue;

        log.debug("락 키 생성 - expression: {}, result: {}", distributedLock.key(), lockKey);

        return lockKey;
    }
}