package com.placementhub.resume;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.placementhub.common.ExternalServiceException;
import com.placementhub.student.StudentProfileResponse;

/**
 * Uploading and viewing resumes. Deliberately NOT @Transactional: storage calls are slow network calls, so
 * they run outside the short transactions in ResumeRecords.
 */
@Service
public class ResumeService {

	private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

	private final ResumeStorage storage;
	private final ResumeRecords records;
	private final ResumeProperties properties;

	ResumeService(ResumeStorage storage, ResumeRecords records, ResumeProperties properties) {
		this.storage = storage;
		this.records = records;
		this.properties = properties;
	}

	/**
	 * Order matters: the new file is stored first, so the database never points at a file that isn't there,
	 * and if this fails the student keeps the resume they had.
	 */
	public StudentProfileResponse uploadForCurrentStudent(MultipartFile file) {
		byte[] pdf = ResumeFileValidator.validate(file);
		String newPath = "students/" + records.currentStudentId() + "/" + UUID.randomUUID() + ".pdf";

		storage.upload(newPath, pdf);

		ResumeRecords.Replaced replaced;
		try {
			replaced = records.replacePath(newPath);
		} catch (RuntimeException ex) {
			deleteQuietly(newPath); // nothing points at the new file, so don't leave it behind
			throw ex;
		}
		if (replaced.oldPath() != null) {
			deleteQuietly(replaced.oldPath());
		}
		return replaced.profile();
	}

	public ResumeLinkResponse linkForCurrentStudent() {
		return linkTo(records.currentStudentPath());
	}

	public ResumeLinkResponse linkForStudent(Long studentId) {
		return linkTo(records.pathOf(studentId));
	}

	private ResumeLinkResponse linkTo(String path) {
		return new ResumeLinkResponse(storage.signedUrl(path, properties.signedUrlTtl()),
				properties.signedUrlTtl().toSeconds());
	}

	/** The student already has their new resume, so a failed clean-up only leaves an unused file behind. */
	private void deleteQuietly(String path) {
		try {
			storage.delete(path);
		} catch (ExternalServiceException ex) {
			log.warn("Could not delete the resume file {}; it is now unused", path);
		}
	}

}
