package com.uireview.service;

import com.uireview.exception.FileTooLargeException;
import com.uireview.exception.InvalidFileTypeException;
import com.uireview.exception.PathTraversalException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

/**
 * Validates uploaded image files before they are processed.
 * Enforces MIME type allowlist, size limits, and path-traversal safety.
 * Requirements: 3.2, 3.3, 11.3
 */
@Service
public class FileValidationService {

    private static final long MAX_SIZE = 10_485_760L; // 10 MB (exclusive)
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp");

    /**
     * Validates the uploaded file.
     * Throws a UIReviewException subtype on any violation.
     */
    public void validate(MultipartFile file) {
        validateMimeType(file);
        validateSize(file);
        validateFilename(file);
    }

    private void validateMimeType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new InvalidFileTypeException(
                    "Unsupported file type: '" + contentType +
                    "'. Accepted types: image/png, image/jpeg, image/webp");
        }
    }

    private void validateSize(MultipartFile file) {
        long size = file.getSize();
        if (size == 0) {
            throw new FileTooLargeException("Uploaded file must not be empty.");
        }
        if (size >= MAX_SIZE) {
            throw new FileTooLargeException(
                    "Uploaded file size " + size + " bytes exceeds the 10 MB limit.");
        }
    }

    private void validateFilename(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name != null && (name.contains("../") || name.startsWith("/"))) {
            throw new PathTraversalException(
                    "Filename contains path traversal characters: " + name);
        }
    }

    /**
     * Computes the SHA-256 hex digest of the supplied bytes.
     * Used for the 24-hour image-hash cache lookup.
     */
    public String computeSha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the JVM spec
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
