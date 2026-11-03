package com.example.batchprocessing.monitoring;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.example.batchprocessing.Product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JobCompletionNotificationListener implements JobExecutionListener {

	private static final Logger log = LoggerFactory.getLogger(JobCompletionNotificationListener.class);

	private final JdbcTemplate jdbcTemplate;
	private final BatchMetrics batchMetrics;

	public JobCompletionNotificationListener(JdbcTemplate jdbcTemplate, BatchMetrics batchMetrics) {
		this.jdbcTemplate = jdbcTemplate;
		this.batchMetrics = batchMetrics;
	}

	@Override
	public void beforeJob(JobExecution jobExecution) {
		setJobLoggingContext(jobExecution);
		LocalDateTime startTime = jobExecution.getStartTime();
		if (startTime == null) {
			startTime = LocalDateTime.now();
		}
		batchMetrics.jobStarted(toTimestampSeconds(startTime));
		log.atInfo()
			.addKeyValue("event", "job_started")
			.log("Job started");
	}

	@Override
	public void afterJob(JobExecution jobExecution) {
		setJobLoggingContext(jobExecution);
		try {
			LocalDateTime startTime = jobExecution.getStartTime();
			LocalDateTime endTime = jobExecution.getEndTime();
			if (endTime == null) {
				endTime = LocalDateTime.now();
			}
			Double durationSeconds = startTime == null
				? null
				: Duration.between(startTime, endTime).toNanos() / 1_000_000_000.0;
			long readCount = jobExecution.getStepExecutions().stream()
				.mapToLong(stepExecution -> stepExecution.getReadCount())
				.sum();
			long writeCount = jobExecution.getStepExecutions().stream()
				.mapToLong(stepExecution -> stepExecution.getWriteCount())
				.sum();
			double endTimestampSeconds = toTimestampSeconds(endTime);
			double metricDurationSeconds = durationSeconds == null
				? endTimestampSeconds - batchMetrics.getActiveStartTimestampSeconds()
				: durationSeconds;
			batchMetrics.jobFinished(jobExecution.getStatus(), metricDurationSeconds,
				endTimestampSeconds, readCount, writeCount);
			var completionLog = jobExecution.getStatus() == BatchStatus.FAILED
				? log.atError()
				: log.atInfo();
			completionLog
				.addKeyValue("event", "job_finished")
				.addKeyValue("status", jobExecution.getStatus().name())
				.addKeyValue("duration_seconds", durationSeconds)
				.addKeyValue("read_count", readCount)
				.addKeyValue("write_count", writeCount)
				.log("Job finished");

			if (jobExecution.getStatus() == BatchStatus.FAILED) {
				jobExecution.getAllFailureExceptions()
					.forEach(failure -> log.error("Job execution failed", failure));
			}

			if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
				log.info("Product import completed. Stored products:");

				jdbcTemplate.query(
					"SELECT productId, productSku, productName, productAmount, productData " +
					"FROM products ORDER BY productId",
					new DataClassRowMapper<>(Product.class)
				).forEach(product -> log.info("Stored product: {}", product));
			}
		} finally {
			MDC.remove("job_name");
			MDC.remove("job_execution_id");
		}
	}

	private double toTimestampSeconds(LocalDateTime dateTime) {
		return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() / 1_000.0;
	}

	private void setJobLoggingContext(JobExecution jobExecution) {
		MDC.put("job_name", jobExecution.getJobInstance().getJobName());
		MDC.put("job_execution_id", String.valueOf(jobExecution.getId()));
	}
}
