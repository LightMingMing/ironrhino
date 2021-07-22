package org.ironrhino.core.spring.configuration;

import java.util.concurrent.Executor;

import org.ironrhino.core.scheduled.ShortCircuitException;
import org.ironrhino.core.throttle.Bulkhead;
import org.ironrhino.core.throttle.Concurrency;
import org.ironrhino.core.throttle.Frequency;
import org.ironrhino.core.throttle.FrequencyLimitExceededException;
import org.ironrhino.core.throttle.Mutex;
import org.ironrhino.core.throttle.RateLimiter;
import org.ironrhino.core.util.IllegalConcurrentAccessException;
import org.ironrhino.core.util.LockFailedException;
import org.ironrhino.core.util.RunnableWithRequestId;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Order(0)
@EnableScheduling
@EnableAsync(order = -999, proxyTargetClass = true)
@Configuration
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class SchedulingConfiguration implements SchedulingConfigurer, AsyncConfigurer {

	@Value("${taskScheduler.poolSize:5}")
	private int taskSchedulerPoolSize = 5;

	@Value("${taskExecutor.corePoolSize:50}")
	private int taskExecutorCorePoolSize = 50;

	@Value("${taskExecutor.maxPoolSize:100}")
	private int taskExecutorMaxPoolSize = 100;

	@Value("${taskExecutor.queueCapacity:10000}")
	private int taskExecutorQueueCapacity = 10000;

	@Override
	public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
		taskRegistrar.setTaskScheduler(taskScheduler());
	}

	@Override
	public Executor getAsyncExecutor() {
		return taskExecutor();
	}

	@Bean
	public TaskScheduler taskScheduler() {
		ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
		threadPoolTaskScheduler.setPoolSize(taskSchedulerPoolSize);
		threadPoolTaskScheduler.setThreadNamePrefix("taskScheduler-");
		threadPoolTaskScheduler.setRemoveOnCancelPolicy(true);
		threadPoolTaskScheduler.setErrorHandler(ex -> {
			String className = ex.getClass().getName();
			if (ex instanceof FrequencyLimitExceededException || ex instanceof IllegalConcurrentAccessException
					|| ex instanceof LockFailedException || ex instanceof ShortCircuitException
					|| className.equals("io.github.resilience4j.bulkhead.BulkheadFullException")
					|| className.equals("io.github.resilience4j.ratelimiter.RequestNotPermitted"))
				log.warn("Error occurred in scheduled task: {}", ex.getLocalizedMessage());
			else
				log.error("Unexpected error occurred in scheduled task", ex);
		});
		return threadPoolTaskScheduler;
	}

	@Bean
	public AsyncTaskExecutor taskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setTaskDecorator(RunnableWithRequestId::new);
		executor.setMaxPoolSize(taskExecutorMaxPoolSize);
		executor.setCorePoolSize(taskExecutorCorePoolSize);
		executor.setQueueCapacity(taskExecutorQueueCapacity);
		executor.setThreadNamePrefix("taskExecutor-");
		executor.setAllowCoreThreadTimeOut(true);
		return executor;
	}

	@Override
	public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
		return (ex, method, args) -> {
			String className = ex.getClass().getName();
			if (AnnotationUtils.findAnnotation(method, Frequency.class) != null
					&& ex instanceof FrequencyLimitExceededException
					|| AnnotationUtils.findAnnotation(method, Concurrency.class) != null
							&& ex instanceof IllegalConcurrentAccessException
					|| AnnotationUtils.findAnnotation(method, RateLimiter.class) != null
							&& className.equals("io.github.resilience4j.ratelimiter.RequestNotPermitted")
					|| AnnotationUtils.findAnnotation(method, Bulkhead.class) != null
							&& className.equals("io.github.resilience4j.bulkhead.BulkheadFullException"))
				log.warn("Error occurred when call method ( " + method.toString() + " ) asynchronously: {}",
						ex.getLocalizedMessage());
			else if (AnnotationUtils.findAnnotation(method, Mutex.class) != null && ex instanceof LockFailedException)
				log.info("Expected error occurred when call method ( " + method.toString() + " ) asynchronously: {}",
						ex.getLocalizedMessage());
			else
				log.error("Unexpected error occurred when call method ( " + method.toString() + " ) asynchronously",
						ex);
		};
	}

}
