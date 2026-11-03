package com.example.batchprocessing.monitoring;

import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.stereotype.Component;

@Component
public class ChunkMetricsListener implements ChunkListener {

	private final BatchMetrics batchMetrics;

	public ChunkMetricsListener(BatchMetrics batchMetrics) {
		this.batchMetrics = batchMetrics;
	}

	@Override
	public void afterChunk(ChunkContext context) {
		StepExecution stepExecution = context.getStepContext().getStepExecution();
		batchMetrics.updateProgress(stepExecution.getReadCount(), stepExecution.getWriteCount());
	}
}
