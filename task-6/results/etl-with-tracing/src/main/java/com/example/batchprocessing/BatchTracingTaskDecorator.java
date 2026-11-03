package com.example.batchprocessing;

import java.util.Map;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

import org.slf4j.MDC;

import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;

@Component
public class BatchTracingTaskDecorator implements TaskDecorator {

	private final Tracer tracer;

	public BatchTracingTaskDecorator(Tracer tracer) {
		this.tracer = tracer;
	}

	@Override
	public Runnable decorate(Runnable task) {
		Span parentSpan = tracer.currentSpan();
		String requestURI = MDC.get("URI");
		return () -> {
			Map<String, String> previousMDC = MDC.getCopyOfContextMap();
			Span jobSpan = tracer.nextSpan(parentSpan).name("importProductJob").start();
			try (Tracer.SpanInScope scope = tracer.withSpan(jobSpan)) {
				if (requestURI == null) {
					MDC.remove("URI");
				} else {
					MDC.put("URI", requestURI);
				}
				task.run();
			} catch (RuntimeException | Error failure) {
				jobSpan.error(failure);
				throw failure;
			} finally {
				try {
					jobSpan.end();
				} finally {
					if (previousMDC == null) {
						MDC.clear();
					} else {
						MDC.setContextMap(previousMDC);
					}
				}
			}
		};
	}
}
