package com.teambind.coupon.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * 스케줄러 설정
 * Spring의 @Scheduled 어노테이션 기반 스케줄링 활성화
 */
@Configuration
@EnableScheduling
public class SchedulerConfig implements SchedulingConfigurer {

    /**
     * 스케줄러 스레드 풀 설정
     * 여러 스케줄된 작업이 동시에 실행될 수 있도록 설정
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(5);  // 동시 실행 가능한 스케줄 작업 수
        taskScheduler.setThreadNamePrefix("coupon-scheduler-");
        taskScheduler.initialize();
        taskRegistrar.setTaskScheduler(taskScheduler);
    }
}