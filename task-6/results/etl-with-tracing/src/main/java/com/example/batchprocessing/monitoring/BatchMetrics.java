package com.example.batchprocessing.monitoring;

import org.springframework.batch.core.BatchStatus;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

@Component
@ManagedResource(objectName = "com.example.batchprocessing:type=BatchMetrics,name=importProductJob")
public class BatchMetrics {

	private static final long INPUT_RECORDS = 5L;

	private long completedCount;
	private long failedCount;
	private double completedDurationSeconds;
	private double failedDurationSeconds;
	private int active;
	private double activeStartTimestampSeconds;
	private double lastFailureTimestampSeconds;
	private long recordsRead;
	private long recordsWritten;

	@ManagedAttribute
	public synchronized long getCompletedCount() {
		return completedCount;
	}

	@ManagedAttribute
	public synchronized long getFailedCount() {
		return failedCount;
	}

	@ManagedAttribute
	public synchronized double getCompletedDurationSeconds() {
		return completedDurationSeconds;
	}

	@ManagedAttribute
	public synchronized double getFailedDurationSeconds() {
		return failedDurationSeconds;
	}

	@ManagedAttribute
	public synchronized int getActive() {
		return active;
	}

	@ManagedAttribute
	public synchronized double getActiveStartTimestampSeconds() {
		return activeStartTimestampSeconds;
	}

	@ManagedAttribute
	public synchronized double getLastFailureTimestampSeconds() {
		return lastFailureTimestampSeconds;
	}

	@ManagedAttribute
	public synchronized long getRecordsRead() {
		return recordsRead;
	}

	@ManagedAttribute
	public synchronized long getRecordsWritten() {
		return recordsWritten;
	}

	@ManagedAttribute
	public long getInputRecords() {
		return INPUT_RECORDS;
	}

	public synchronized void jobStarted(double startTimestampSeconds) {
		active = 1;
		activeStartTimestampSeconds = startTimestampSeconds;
		recordsRead = 0;
		recordsWritten = 0;
	}

	public synchronized void updateProgress(long readCount, long writeCount) {
		recordsRead = readCount;
		recordsWritten = writeCount;
	}

	public synchronized void jobFinished(BatchStatus status, double durationSeconds,
			double endTimestampSeconds, long readCount, long writeCount) {
		if (active == 0) {
			return;
		}
		if (status == BatchStatus.COMPLETED) {
			completedCount++;
			completedDurationSeconds += durationSeconds;
		} else if (status == BatchStatus.FAILED) {
			failedCount++;
			failedDurationSeconds += durationSeconds;
			lastFailureTimestampSeconds = endTimestampSeconds;
		}
		recordsRead = readCount;
		recordsWritten = writeCount;
		active = 0;
		activeStartTimestampSeconds = 0;
	}
}
