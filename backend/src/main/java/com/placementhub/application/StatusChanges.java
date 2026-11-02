package com.placementhub.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.placementhub.audit.AuditAction;
import com.placementhub.audit.AuditEntityType;
import com.placementhub.audit.AuditService;

/**
 * The only place that changes an application's status. Every change writes an audit row
 * (old → new status and reason, plus the trigger), so no code path can forget the audit.
 * Call it inside the transaction that makes the change.
 */
@Component
public class StatusChanges {

	/** The length of applications.status_reason (VARCHAR(300)). */
	static final int MAX_REASON_LENGTH = 300;

	private final AuditService auditService;

	public StatusChanges(AuditService auditService) {
		this.auditService = auditService;
	}

	/** A new application was saved (it must already have an id). */
	public void created(Application application, StatusChangeTrigger trigger) {
		auditService.record(AuditEntityType.APPLICATION, application.getId(), AuditAction.STATUS_CHANGED, null,
				value(application.getStatus(), application.getStatusReason(), trigger));
	}

	/**
	 * Sets the new status and reason. Writes an audit row only if one of them actually changed.
	 * @return true if something changed
	 */
	public boolean change(Application application, ApplicationStatus status, String reason,
			StatusChangeTrigger trigger) {
		reason = fitReason(reason);
		ApplicationStatus oldStatus = application.getStatus();
		String oldReason = application.getStatusReason();
		if (oldStatus == status && Objects.equals(oldReason, reason)) {
			return false;
		}
		application.setStatus(status);
		application.setStatusReason(reason);
		auditService.record(AuditEntityType.APPLICATION, application.getId(), AuditAction.STATUS_CHANGED,
				value(oldStatus, oldReason, null), value(status, reason, trigger));
		return true;
	}

	/** Long policy reasons (several joined) are cut with "..." rather than failing the whole transaction. */
	static String fitReason(String reason) {
		if (reason == null || reason.length() <= MAX_REASON_LENGTH) {
			return reason;
		}
		return reason.substring(0, MAX_REASON_LENGTH - 3) + "...";
	}

	/** LinkedHashMap, not Map.of: the reason may be null, and the JSON keys keep a fixed order. */
	private static Map<String, Object> value(ApplicationStatus status, String reason, StatusChangeTrigger trigger) {
		Map<String, Object> value = new LinkedHashMap<>();
		value.put("status", status.name());
		value.put("reason", reason);
		if (trigger != null) {
			value.put("trigger", trigger.name());
		}
		return value;
	}

}
