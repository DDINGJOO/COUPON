package com.teambind.coupon.adapter.out.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RedisDistributedLock 단위 테스트
 * Redis 연결 없이 순수 로직 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RedisDistributedLock 단위 테스트")
class RedisDistributedLockUnitTest {

    @InjectMocks
    private RedisDistributedLock distributedLock;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private static final String TEST_KEY = "test:lock:key";
    private static final String TEST_VALUE = "test-value-123";
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(10);

    @BeforeEach
    void setUp() {
        // Mock 설정은 각 테스트에서 필요할 때만 수행
    }

    @Test
    @DisplayName("락 획득 성공")
    void tryLock_Success() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);

        // when
        boolean result = distributedLock.tryLock(TEST_KEY, TEST_VALUE, TEST_TIMEOUT);

        // then
        assertThat(result).isTrue();
        verify(valueOperations).setIfAbsent(
                eq("lock:" + TEST_KEY),
                eq(TEST_VALUE),
                eq(TEST_TIMEOUT)
        );
    }

    @Test
    @DisplayName("락 획득 실패 - 이미 다른 프로세스가 획득")
    void tryLock_Failed_AlreadyLocked() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        // when
        boolean result = distributedLock.tryLock(TEST_KEY, TEST_VALUE, TEST_TIMEOUT);

        // then
        assertThat(result).isFalse();
        verify(valueOperations).setIfAbsent(
                eq("lock:" + TEST_KEY),
                eq(TEST_VALUE),
                eq(TEST_TIMEOUT)
        );
    }

    @Test
    @DisplayName("락 획득 실패 - Redis 응답 null")
    void tryLock_Failed_NullResponse() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(null);

        // when
        boolean result = distributedLock.tryLock(TEST_KEY, TEST_VALUE, TEST_TIMEOUT);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("락 획득 시 예외 발생")
    void tryLock_Exception() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("Redis connection error"));

        // when
        boolean result = distributedLock.tryLock(TEST_KEY, TEST_VALUE, TEST_TIMEOUT);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("락 해제 성공")
    void unlock_Success() {
        // given
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any()))
                .thenReturn(1L);

        // when
        boolean result = distributedLock.unlock(TEST_KEY, TEST_VALUE);

        // then
        assertThat(result).isTrue();
        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                eq(Collections.singletonList("lock:" + TEST_KEY)),
                eq(TEST_VALUE)
        );
    }

    @Test
    @DisplayName("락 해제 실패 - 다른 값으로 시도")
    void unlock_Failed_WrongValue() {
        // given
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any()))
                .thenReturn(0L);

        // when
        boolean result = distributedLock.unlock(TEST_KEY, "wrong-value");

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("락 해제 시 예외 발생")
    void unlock_Exception() {
        // given
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any()))
                .thenThrow(new RuntimeException("Redis connection error"));

        // when
        boolean result = distributedLock.unlock(TEST_KEY, TEST_VALUE);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("여러 키에 대한 동시 락 획득")
    void tryLock_MultipleLocks() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);

        // when
        boolean result1 = distributedLock.tryLock("key1", "value1", TEST_TIMEOUT);
        boolean result2 = distributedLock.tryLock("key2", "value2", TEST_TIMEOUT);
        boolean result3 = distributedLock.tryLock("key3", "value3", TEST_TIMEOUT);

        // then
        assertThat(result1).isTrue();
        assertThat(result2).isTrue();
        assertThat(result3).isTrue();
        verify(valueOperations, times(3)).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("동일 키에 대한 중복 락 획득 시도")
    void tryLock_DuplicateKey() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true)
                .thenReturn(false); // 두 번째 시도는 실패

        // when
        boolean firstAttempt = distributedLock.tryLock(TEST_KEY, TEST_VALUE, TEST_TIMEOUT);
        boolean secondAttempt = distributedLock.tryLock(TEST_KEY, "different-value", TEST_TIMEOUT);

        // then
        assertThat(firstAttempt).isTrue();
        assertThat(secondAttempt).isFalse();
    }

    @Test
    @DisplayName("타임아웃 시간 변경 테스트")
    void tryLock_DifferentTimeouts() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);

        Duration shortTimeout = Duration.ofSeconds(1);
        Duration longTimeout = Duration.ofMinutes(5);

        // when
        boolean shortResult = distributedLock.tryLock("short-key", "value", shortTimeout);
        boolean longResult = distributedLock.tryLock("long-key", "value", longTimeout);

        // then
        assertThat(shortResult).isTrue();
        assertThat(longResult).isTrue();

        verify(valueOperations).setIfAbsent(eq("lock:short-key"), anyString(), eq(shortTimeout));
        verify(valueOperations).setIfAbsent(eq("lock:long-key"), anyString(), eq(longTimeout));
    }

    @Test
    @DisplayName("락 키 프리픽스 검증")
    void tryLock_VerifyLockPrefix() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);

        // when
        distributedLock.tryLock("coupon:1234", "value", TEST_TIMEOUT);

        // then
        verify(valueOperations).setIfAbsent(
                eq("lock:coupon:1234"),
                eq("value"),
                any(Duration.class)
        );
    }
}