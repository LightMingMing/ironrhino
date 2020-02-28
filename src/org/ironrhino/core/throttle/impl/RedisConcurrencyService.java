package org.ironrhino.core.throttle.impl;

import static org.ironrhino.core.metadata.Profiles.CLOUD;
import static org.ironrhino.core.metadata.Profiles.CLUSTER;
import static org.ironrhino.core.metadata.Profiles.DUAL;

import java.util.concurrent.TimeUnit;

import org.ironrhino.core.spring.configuration.PriorityQualifier;
import org.ironrhino.core.spring.configuration.ServiceImplementationConditional;
import org.ironrhino.core.throttle.ConcurrencyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component("concurrencyService")
@ServiceImplementationConditional(profiles = { DUAL, CLOUD, CLUSTER })
public class RedisConcurrencyService implements ConcurrencyService {

	private static final String NAMESPACE = "concurrency:";

	@Autowired
	@Qualifier("stringRedisTemplate")
	@PriorityQualifier
	private StringRedisTemplate throttleStringRedisTemplate;

	@Override
	public boolean tryAcquire(String name, int permits) {
		String key = NAMESPACE + name;
		Long value = throttleStringRedisTemplate.opsForValue().increment(key, 1);
		boolean success = value != null && value.intValue() <= permits;
		if (!success)
			throttleStringRedisTemplate.opsForValue().increment(key, -1);
		return success;
	}

	@Override
	public boolean tryAcquire(String name, int permits, long timeout, TimeUnit unit) throws InterruptedException {
		if (timeout <= 0)
			return tryAcquire(name, permits);
		String key = NAMESPACE + name;
		Long value = throttleStringRedisTemplate.opsForValue().increment(key, 1);
		boolean success = value != null && value.intValue() <= permits;
		if (!success)
			throttleStringRedisTemplate.opsForValue().increment(key, -1);
		long millisTimeout = unit.toMillis(timeout);
		long start = System.nanoTime();
		while (!success) {
			Thread.sleep(100);
			if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) >= millisTimeout)
				break;
			value = throttleStringRedisTemplate.opsForValue().increment(key, 1);
			success = value != null && value.intValue() <= permits;
			if (!success)
				throttleStringRedisTemplate.opsForValue().increment(key, -1);
		}
		return success;
	}

	@Override
	public void acquire(String name, int permits) {
		String key = NAMESPACE + name;
		Long value = throttleStringRedisTemplate.opsForValue().increment(key, 1);
		boolean success = value != null && value.intValue() <= permits;
		if (!success)
			throttleStringRedisTemplate.opsForValue().increment(key, -1);
		while (!success) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			value = throttleStringRedisTemplate.opsForValue().increment(key, 1);
			success = value != null && value.intValue() <= permits;
			if (!success)
				throttleStringRedisTemplate.opsForValue().increment(key, -1);
		}

	}

	@Override
	public void release(String name) {
		String key = NAMESPACE + name;
		throttleStringRedisTemplate.opsForValue().increment(key, -1);
	}

}
