package org.ironrhino.core.util;

public class Snowflake {

	private final static long EPOCH = 1556150400000L;

	private final int workerId;
	private final long workerIdBits;
	private final long sequenceBits;
	private final long sequenceMask;

	private long sequence = 0L;
	private long lastTimestamp = -1L;

	public Snowflake(int workerId) {
		this(workerId, 8, 10);
	}

	public Snowflake(int workerId, int workerIdBits, int sequenceBits) {
		long maxWorkerId = -1L ^ -1L << workerIdBits;
		if (workerId > maxWorkerId || workerId < 0) {
			throw new IllegalArgumentException(
					String.format("workerId can't be greater than %d or less than 0", maxWorkerId));
		}
		this.workerId = workerId;
		this.workerIdBits = workerIdBits;
		this.sequenceBits = sequenceBits;
		this.sequenceMask = -1L ^ -1L << sequenceBits;
	}

	public synchronized long nextId() {
		long timestamp = System.currentTimeMillis();
		if (timestamp == lastTimestamp) {
			sequence = (sequence + 1) & sequenceMask;
			if (sequence == 0) {
				timestamp = System.currentTimeMillis();
				while (timestamp <= lastTimestamp) {
					timestamp = System.currentTimeMillis();
				}
			}
		} else if (timestamp > lastTimestamp) {
			sequence = 0;
		} else {
			throw new IllegalStateException(String.format(
					"Clock moved backwards. Refusing to generate id for %d milliseconds", lastTimestamp - timestamp));
		}
		lastTimestamp = timestamp;
		return ((timestamp - EPOCH) << (sequenceBits + workerIdBits)) | (workerId << sequenceBits) | sequence;
	}

}
