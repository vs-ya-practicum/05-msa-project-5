package com.example.batchprocessing;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class BatchJobRunner {

	private final JobLauncher jobLauncher;
	private final Job importProductJob;

	public BatchJobRunner(JobLauncher jobLauncher, @Qualifier("importProductJob") Job importProductJob) {
		this.jobLauncher = jobLauncher;
		this.importProductJob = importProductJob;
	}

	public JobExecution launchJob() throws JobExecutionAlreadyRunningException,
			JobRestartException, JobInstanceAlreadyCompleteException, JobParametersInvalidException {
		return jobLauncher.run(importProductJob, new JobParameters());
	}
}
