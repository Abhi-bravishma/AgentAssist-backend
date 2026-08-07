package com.agentassist.rag;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * VERBATIM COPY of bravishma-rag's {@code FileStorageService}, brought in-app.
 * Deltas: package, and the property keys moved from {@code app.agentAssist.fileStorage.*}
 * to {@code rag.internal.file-storage.*}. Files are organized by companyId for
 * multi-tenant isolation; the docker-compose volume ./agent-assist-files
 * already exists for this path.
 */
@Slf4j
@Service
public class FileStorageService {

    @Value("${rag.internal.file-storage.path:./agent-assist-files}")
    private String storagePath;

    @Value("${rag.internal.file-storage.enabled:true}")
    private boolean enabled;

    private Path rootLocation;

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("File storage is disabled");
            return;
        }

        rootLocation = Paths.get(storagePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootLocation);
            log.info("File storage initialized at: {}", rootLocation);
        } catch (IOException e) {
            log.error("Could not initialize file storage at {}: {}", rootLocation, e.getMessage());
            throw new RuntimeException("Could not initialize file storage", e);
        }
    }

    /**
     * Check if file storage is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Store a file for a specific company.
     *
     * @param file      The file to store
     * @param companyId The company ID for isolation
     * @return The stored file name
     */
    public String store(MultipartFile file, Long companyId) throws IOException {
        if (!enabled) {
            log.debug("File storage disabled, skipping store");
            return null;
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("File name cannot be empty");
        }

        // Sanitize filename to prevent path traversal
        fileName = sanitizeFileName(fileName);

        // Create company-specific directory
        Path companyDir = rootLocation.resolve(String.valueOf(companyId));
        Files.createDirectories(companyDir);

        // Store file
        Path targetPath = companyDir.resolve(fileName);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        log.info("Stored file: {} for companyId: {} at {}", fileName, companyId, targetPath);
        return fileName;
    }

    /**
     * Load a file as a Resource.
     *
     * @param fileName  The file name to load
     * @param companyId The company ID
     * @return Resource for the file
     */
    public Resource loadAsResource(String fileName, Long companyId) {
        if (!enabled) {
            log.debug("File storage disabled");
            return null;
        }

        try {
            // Sanitize filename
            fileName = sanitizeFileName(fileName);

            Path filePath = rootLocation.resolve(String.valueOf(companyId)).resolve(fileName).normalize();

            // Security check: ensure file is within allowed directory
            if (!filePath.startsWith(rootLocation)) {
                log.warn("Attempted path traversal attack: {}", fileName);
                throw new RuntimeException("Access denied");
            }

            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                log.debug("Loading file: {} for companyId: {}", fileName, companyId);
                return resource;
            } else {
                log.warn("File not found: {} for companyId: {}", fileName, companyId);
                return null;
            }
        } catch (MalformedURLException e) {
            log.error("Error loading file {}: {}", fileName, e.getMessage());
            return null;
        }
    }

    /**
     * Check if a file exists.
     *
     * @param fileName  The file name
     * @param companyId The company ID
     * @return true if file exists
     */
    public boolean exists(String fileName, Long companyId) {
        if (!enabled) {
            return false;
        }

        fileName = sanitizeFileName(fileName);
        Path filePath = rootLocation.resolve(String.valueOf(companyId)).resolve(fileName);
        return Files.exists(filePath);
    }

    /**
     * Delete a file.
     *
     * @param fileName  The file name to delete
     * @param companyId The company ID
     * @return true if deleted successfully
     */
    public boolean delete(String fileName, Long companyId) {
        if (!enabled) {
            return false;
        }

        try {
            fileName = sanitizeFileName(fileName);
            Path filePath = rootLocation.resolve(String.valueOf(companyId)).resolve(fileName);

            // Security check
            if (!filePath.startsWith(rootLocation)) {
                log.warn("Attempted path traversal in delete: {}", fileName);
                return false;
            }

            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.info("Deleted file: {} for companyId: {}", fileName, companyId);
            }
            return deleted;
        } catch (IOException e) {
            log.error("Error deleting file {}: {}", fileName, e.getMessage());
            return false;
        }
    }

    /**
     * Sanitize file name to prevent path traversal attacks.
     */
    private String sanitizeFileName(String fileName) {
        // Remove any path components, keep only the file name
        return Paths.get(fileName).getFileName().toString();
    }

    /**
     * Get the content type for a file based on extension.
     */
    public String getContentType(String fileName) {
        String extension = getFileExtension(fileName).toLowerCase();
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "txt" -> "text/plain";
            case "md" -> "text/markdown";
            case "csv" -> "text/csv";
            default -> "application/octet-stream";
        };
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }
}
