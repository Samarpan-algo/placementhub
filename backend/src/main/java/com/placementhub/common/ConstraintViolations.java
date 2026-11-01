package com.placementhub.common;

import org.hibernate.exception.ConstraintViolationException;

/** Helpers for reading which DB constraint a failed write violated. */
public final class ConstraintViolations {

	private ConstraintViolations() {
	}

	/** Walks the cause chain to find Hibernate's constraint name (e.g. "uq_companies_name"), or null. */
	public static String constraintName(Throwable ex) {
		for (Throwable t = ex; t != null; t = t.getCause()) {
			if (t instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
				return cve.getConstraintName();
			}
		}
		return null;
	}

	public static boolean isViolationOf(Throwable ex, String constraint) {
		return constraint.equalsIgnoreCase(constraintName(ex));
	}

}
