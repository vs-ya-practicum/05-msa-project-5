package com.example.batchprocessing;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BatchJobRunner implements ApplicationRunner, DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(BatchJobRunner.class);

	private final JobLauncher jobLauncher;
	private final Job importProductJob;
	private final ExecutorService worker;

	public BatchJobRunner(JobLauncher jobLauncher, @Qualifier("importProductJob") Job importProductJob) {
		this.jobLauncher = jobLauncher;
		this.importProductJob = importProductJob;
		this.worker = Executors.newSingleThreadExecutor(task -> {
			Thread thread = new Thread(task, "batch-job-worker");
			thread.setDaemon(false);
			return thread;
		});
	}

	@Override
	public void run(ApplicationArguments arguments) {
		worker.submit(this::launchJob);
	}

	@Override
	public void destroy() {
		worker.shutdownNow();
	}

	private void launchJob() {
		try {
			jobLauncher.run(importProductJob, new JobParameters());
		} catch (Exception exception) {
			log.atError()
				.addKeyValue("job_name", importProductJob.getName())
				.setCause(exception)
				.log("Could not launch batch job");
		}
	}
}
