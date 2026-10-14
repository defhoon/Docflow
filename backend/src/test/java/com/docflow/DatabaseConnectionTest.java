package com.docflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class DatabaseConnectionTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void connectsToPostgreSql() {
		String productName = jdbcTemplate.execute(
				(ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());

		assertThat(productName).isEqualTo("PostgreSQL");
	}

	@Test
	void appliesBaselineMigration() {
		Boolean success = jdbcTemplate.queryForObject(
				"SELECT success FROM flyway_schema_history WHERE version = '1'", Boolean.class);

		assertThat(success).isTrue();
	}

}
