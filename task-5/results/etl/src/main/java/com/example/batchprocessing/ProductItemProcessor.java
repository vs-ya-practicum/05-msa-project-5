package com.example.batchprocessing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

public class ProductItemProcessor implements ItemProcessor<Product, Product> {

	private static final Logger log = LoggerFactory.getLogger(ProductItemProcessor.class);

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Value("${batch.demo.fail-on-sku:0}")
	private long failOnSKU;

	@Value("${batch.demo.item-delay-ms:0}")
	private long itemDelayMS;

	@Override
	public Product process(final Product product) {
		if (failOnSKU > 0 && product.productSku() != null && product.productSku() == failOnSKU) {
			throw new IllegalStateException("Demonstration failure requested for SKU " + failOnSKU);
		}

		if (itemDelayMS > 0) {
			try {
				Thread.sleep(itemDelayMS);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Demonstration delay interrupted", exception);
			}
		}

		var matches = jdbcTemplate.query(
			"SELECT productSku, loyalityData FROM loyality_data WHERE productSku = ?",
			new DataClassRowMapper<>(Loyality.class),
			product.productSku()
		);

		Product result = product;

		if (!matches.isEmpty()) {
			result = new Product(
				product.productId(),
				product.productSku(),
				product.productName(),
				product.productAmount(),
				matches.get(0).loyalityData()
			);
		}

		log.info("Transforming ({}) into ({})", product, result);
		return result;
	}

}
