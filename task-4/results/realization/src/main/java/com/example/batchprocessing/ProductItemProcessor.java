package com.example.batchprocessing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

public class ProductItemProcessor implements ItemProcessor<Product, Product> {

	private static final Logger log = LoggerFactory.getLogger(ProductItemProcessor.class);

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Override
	public Product process(final Product product) {
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
