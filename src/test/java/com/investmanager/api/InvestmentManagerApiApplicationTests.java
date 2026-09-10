package com.investmanager.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
		"brapi.token=test-token",
		"jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
		"jwt.expiration=3600000"
})
@Testcontainers
class InvestmentManagerApiApplicationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres =
			new PostgreSQLContainer("postgres:17-alpine");

	@Test
	void contextLoads() {
	}

}
