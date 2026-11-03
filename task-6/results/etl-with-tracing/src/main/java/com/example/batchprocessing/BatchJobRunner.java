package com.example.batchprocessing;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Component
public class BatchJobRunner {

	private final JobLauncher jobLauncher;
	private final Job importProductJob;
	private final JobRepository jobRepository;

	public BatchJobRunner(@Qualifier("apiJobLauncher") JobLauncher jobLauncher,
			@Qualifier("importProductJob") Job importProductJob, JobRepository jobRepository) {
		this.jobLauncher = jobLauncher;
		this.importProductJob = importProductJob;
		this.jobRepository = jobRepository;
	}

	public JobExecution launchJob() throws JobExecutionAlreadyRunningException,
			JobRestartException, JobInstanceAlreadyCompleteException, JobParametersInvalidException {
		JobParameters parameters = new JobParametersBuilder()
			.addString("input", "product-data.csv", true)
			.toJobParameters();
		JobExecution execution;
		try {
			execution = jobLauncher.run(importProductJob, parameters);
		} catch (ConcurrencyFailureException | DuplicateKeyException exception) {
			translateLaunchConflict(parameters, exception);
			throw exception;
		}
		// The launcher records executor rejection as FAILED without starting the job.
		if (execution.getStatus() == BatchStatus.FAILED && execution.getStartTime() == null) {
			throw new TaskRejectedException("Job executor rejected execution " + execution.getId()
				+ ": " + execution.getExitStatus().getExitDescription());
		}
		return execution;
	}

	private void translateLaunchConflict(JobParameters parameters, RuntimeException failure)
			throws JobExecutionAlreadyRunningException, JobInstanceAlreadyCompleteException, JobRestartException {
		JobExecution latestExecution;
		try {
			latestExecution = jobRepository.getLastJobExecution(importProductJob.getName(), parameters);
		} catch (RuntimeException lookupFailure) {
			failure.addSuppressed(lookupFailure);
			throw failure;
		}
		if (latestExecution == null) {
			return;
		}
		if (latestExecution.getStatus().isRunning()) {
			JobExecutionAlreadyRunningException conflict =
				new JobExecutionAlreadyRunningException("Job already running");
			conflict.initCause(failure);
			throw conflict;
		}
		if (latestExecution.getStatus() == BatchStatus.COMPLETED) {
			JobInstanceAlreadyCompleteException conflict =
				new JobInstanceAlreadyCompleteException("Job already completed");
			conflict.initCause(failure);
			throw conflict;
		}
		if (latestExecution.getStatus() == BatchStatus.UNKNOWN || !importProductJob.isRestartable()) {
			JobRestartException conflict = new JobRestartException("Job restart not allowed");
			conflict.initCause(failure);
			throw conflict;
		}
	}
}
