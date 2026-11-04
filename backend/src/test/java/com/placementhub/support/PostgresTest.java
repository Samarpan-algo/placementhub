package com.placementhub.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

/**
 * A repository test against the real PostgreSQL TEST database (the "test" profile), with the real Flyway schema.
 * Needed where H2 or mocks can't help: native SQL (percentile_cont), JSONB, null-parameter binding,
 * and counting the SQL statements a service runs (N+1 checks, via Hibernate statistics).
 *
 * Every test runs in a transaction that is ROLLED BACK at the end (@DataJpaTest's default). Some tests
 * delete rows inside their transaction to start from a known state; the rollback restores them.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
public @interface PostgresTest {
}
