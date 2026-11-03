package com.example.batchprocessing;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
// Spring Boot registers its HTTP observation filter at HIGHEST_PRECEDENCE + 1.
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RequestLoggingFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !"POST".equals(request.getMethod())
			|| !request.getRequestURI().equals(request.getContextPath() + "/api/jobs/import-products");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String previousURI = MDC.get("URI");
		MDC.put("URI", request.getRequestURI());
		try {
			log.atInfo()
				.addKeyValue("event", "api_request_started")
				.addKeyValue("http_method", request.getMethod())
				.log("API request started");
			filterChain.doFilter(request, response);
		} finally {
			try {
				log.atInfo()
					.addKeyValue("event", "api_request_finished")
					.addKeyValue("http_method", request.getMethod())
					.addKeyValue("http_status", response.getStatus())
					.log("API request finished");
			} finally {
				if (previousURI == null) {
					MDC.remove("URI");
				} else {
					MDC.put("URI", previousURI);
				}
			}
		}
	}
}
