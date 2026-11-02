package com.placementhub.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

/**
 * A repository test against the real local PostgreSQL (docker-compose), with the real Flyway schema.
 * Needed where H2 or mocks can't help: native SQL (percentile_cont), JSONB, null-parameter binding,
 * and counting the SQL statements a service runs (N+1 checks, via Hibernate statistics).
 *
 * Every test runs in a transaction that is ROLLED BACK at the end (@DataJpaTest's default), so the dev
 * data is never changed. Some tests delete rows inside their transaction to start from a known state;
 * the rollback restores them.
 *
 * TODO Milestone 11: run all DB tests against a separate test database (e.g. a second docker-compose
 * service or a CI Postgres service), so they never depend on, or lock rows in, the dev database.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public @interface PostgresTest {
}
