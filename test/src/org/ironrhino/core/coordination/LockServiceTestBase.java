package org.ironrhino.core.coordination;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class LockServiceTestBase {

	public static final int THREADS = 100;

	public static final int LOOP = 1000;

	private static ExecutorService executorService;

	@Autowired
	private LockService lockService;

	@BeforeClass
	public static void setup() {
		executorService = Executors.newFixedThreadPool(THREADS);
	}

	@AfterClass
	public static void destroy() {
		executorService.shutdown();
	}

	@Test
	public void testTryLock() throws InterruptedException {
		final ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();
		final CountDownLatch cdl = new CountDownLatch(THREADS);
		final AtomicInteger count = new AtomicInteger();
		final AtomicInteger error = new AtomicInteger();
		long time = System.currentTimeMillis();
		for (int i = 0; i < THREADS; i++) {

			executorService.execute(() -> {
				for (int j = 0; j < LOOP; j++) {
					try {
						String lockName = "lock" + System.currentTimeMillis() % 10;
						if (lockService.tryLock(lockName))
							try {
								try {
									Thread.sleep(1);
									if (map.putIfAbsent(lockName, lockName) != null)
										error.incrementAndGet();
									if (!map.remove(lockName, lockName))
										error.incrementAndGet();
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
							} finally {
								lockService.unlock(lockName);
							}
						count.incrementAndGet();
					} catch (Exception e) {
						error.incrementAndGet();
						e.printStackTrace();
					}

				}
				cdl.countDown();
			});
		}
		cdl.await();
		time = System.currentTimeMillis() - time;
		System.out.println("completed " + count.get() + " requests with concurrency(" + THREADS + ") in " + time
				+ "ms (tps = " + (count.get() * 1000 / time) + ") with tryLock()");
		assertThat(error.get(), is(0));
	}

	@Test
	public void testLock() throws InterruptedException {
		final ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();
		final CountDownLatch cdl = new CountDownLatch(THREADS);
		final AtomicInteger count = new AtomicInteger();
		final AtomicInteger error = new AtomicInteger();
		long time = System.currentTimeMillis();
		for (int i = 0; i < THREADS; i++) {

			executorService.execute(() -> {
				for (int j = 0; j < LOOP / THREADS; j++) {
					try {
						String lockName = "lock" + System.currentTimeMillis() % 10;
						lockService.lock(lockName);
						try {
							try {
								Thread.sleep(1);
								if (map.putIfAbsent(lockName, lockName) != null)
									error.incrementAndGet();
								if (!map.remove(lockName, lockName))
									error.incrementAndGet();
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						} finally {
							lockService.unlock(lockName);
						}
						count.incrementAndGet();
					} catch (Exception e) {
						error.incrementAndGet();
						e.printStackTrace();
					}

				}
				cdl.countDown();
			});
		}
		cdl.await();
		time = System.currentTimeMillis() - time;
		System.out.println("completed " + count.get() + " requests with concurrency(" + THREADS + ") in " + time
				+ "ms (tps = " + (count.get() * 1000 / time) + ") with lock()");
		assertThat(error.get(), is(0));
	}

}
