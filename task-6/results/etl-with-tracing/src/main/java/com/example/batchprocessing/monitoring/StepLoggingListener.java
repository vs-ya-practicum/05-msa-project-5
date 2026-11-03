package com.example.batchprocessing.monitoring;

import java.time.Duration;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.stereotype.Component;

@Component
public class StepLoggingListener implements StepExecutionListener {

	private static final Logger log = LoggerFactory.getLogger(StepLoggingListener.class);

	@Override
	public void beforeStep(StepExecution stepExecution) {
		setStepLoggingContext(stepExecution);
		log.atInfo()
			.addKeyValue("event", "step_started")
			.log("Step started");
	}

	@Override
	public ExitStatus afterStep(StepExecution stepExecution) {
		setStepLoggingContext(stepExecution);
		try {
			LocalDateTime startTime = stepExecution.getStartTime();
			LocalDateTime endTime = stepExecution.getEndTime();
			if (endTime == null) {
				endTime = LocalDateTime.now();
			}
			Double durationSeconds = startTime == null
				? null
				: Duration.between(startTime, endTime).toNanos() / 1_000_000_000.0;
			var completionLog = stepExecution.getStatus() == BatchStatus.FAILED
				? log.atError()
				: log.atInfo();
			completionLog
				.addKeyValue("event", "step_finished")
				.addKeyValue("status", stepExecution.getStatus().name())
				.addKeyValue("duration_seconds", durationSeconds)
				.addKeyValue("read_count", stepExecution.getReadCount())
				.addKeyValue("write_count", stepExecution.getWriteCount())
				.log("Step finished");

			if (stepExecution.getStatus() == BatchStatus.FAILED) {
				stepExecution.getFailureExceptions()
					.forEach(failure -> log.error("Step execution failed", failure));
			}
			return null;
		} finally {
			MDC.remove("step_name");
			MDC.remove("step_execution_id");
		}
	}

	private void setStepLoggingContext(StepExecution stepExecution) {
		MDC.put("step_name", stepExecution.getStepName());
		MDC.put("step_execution_id", String.valueOf(stepExecution.getId()));
	}
}
