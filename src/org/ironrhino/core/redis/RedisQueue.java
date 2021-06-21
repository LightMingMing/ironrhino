package org.ironrhino.core.redis;

import java.io.Serializable;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.ironrhino.core.spring.configuration.PriorityQualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ResolvableType;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.support.collections.DefaultRedisList;

import lombok.Setter;

public abstract class RedisQueue<T extends Serializable> implements org.ironrhino.core.message.Queue<T> {

	protected Logger logger = LoggerFactory.getLogger(getClass());

	@Setter
	protected String queueName;

	@Setter
	protected boolean consuming;

	private AtomicBoolean stopConsuming = new AtomicBoolean();

	private Thread worker;

	@Autowired(required = false)
	private ExecutorService executorService;

	@Setter
	@Autowired
	@PriorityQualifier({ "mqRedisTemplate", "globalRedisTemplate" })
	private RedisTemplate<String, T> mqRedisTemplate;

	protected BlockingDeque<T> queue;

	public RedisQueue() {
		Class<?> clazz = ResolvableType.forClass(getClass()).as(RedisQueue.class).resolveGeneric(0);
		if (clazz == null)
			throw new IllegalArgumentException(getClass().getName() + " should be generic");
		queueName = clazz.getName();
	}

	@PostConstruct
	public void afterPropertiesSet() {
		queue = new DefaultRedisList<>(queueName, mqRedisTemplate);
		if (consuming) {
			Runnable task = () -> {
				while (!stopConsuming.get()) {
					try {
						T message = queue.take();
						consume(message);
					} catch (Throwable e) {
						logger.error(e.getMessage(), e);
					}
				}
			};
			if (executorService != null) {
				executorService.execute(task);
			} else {
				worker = new Thread(task);
				worker.start();
			}
		}
	}

	@PreDestroy
	public void stop() {
		stopConsuming.set(true);
		if (worker != null)
			worker.interrupt();
	}

	@Override
	public void produce(T message) {
		queue.add(message);
	}

}
