package com.example.batchprocessing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BatchJobController {

	private static final Logger log = LoggerFactory.getLogger(BatchJobController.class);

	private final BatchJobRunner batchJobRunner;

	public BatchJobController(BatchJobRunner batchJobRunner) {
		this.batchJobRunner = batchJobRunner;
	}

	@PostMapping("/api/jobs/import-products")
	public ResponseEntity<LaunchResponse> launchJob() throws JobExecutionAlreadyRunningException,
			JobRestartException, JobInstanceAlreadyCompleteException, JobParametersInvalidException {
		JobExecution execution = batchJobRunner.launchJob();
		LaunchResponse response = new LaunchResponse(
			execution.getJobInstance().getJobName(),
			execution.getId(),
			"Job launch accepted"
		);
		return ResponseEntity.accepted().body(response);
	}

	@ExceptionHandler(JobExecutionAlreadyRunningException.class)
	public ResponseEntity<ErrorResponse> handleJobAlreadyRunning() {
		return ResponseEntity.status(409).body(
			new ErrorResponse("JOB_ALREADY_RUNNING", "Job already running")
		);
	}

	@ExceptionHandler(JobInstanceAlreadyCompleteException.class)
	public ResponseEntity<ErrorResponse> handleJobAlreadyCompleted() {
		return ResponseEntity.status(409).body(
			new ErrorResponse("JOB_ALREADY_COMPLETED", "Job already completed")
		);
	}

	@ExceptionHandler(JobRestartException.class)
	public ResponseEntity<ErrorResponse> handleJobRestartNotAllowed() {
		return ResponseEntity.status(409).body(
			new ErrorResponse("JOB_RESTART_NOT_ALLOWED", "Job restart not allowed")
		);
	}

	@ExceptionHandler(TaskRejectedException.class)
	public ResponseEntity<ErrorResponse> handleJobLaunchUnavailable(TaskRejectedException exception) {
		log.warn("Batch job executor could not accept the launch", exception);
		return ResponseEntity.status(503).body(
			new ErrorResponse("JOB_LAUNCH_UNAVAILABLE", "Job executor is temporarily unavailable")
		);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleJobLaunchFailed(Exception exception) {
		log.error("Could not launch batch job", exception);
		return ResponseEntity.status(500).body(
			new ErrorResponse("JOB_LAUNCH_FAILED", "Could not launch batch job")
		);
	}

	public record LaunchResponse(String jobName, Long executionId, String message) {
	}

	public record ErrorResponse(String code, String message) {
	}
}
