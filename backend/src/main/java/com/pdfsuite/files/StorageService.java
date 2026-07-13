package com.pdfsuite.files;

import com.pdfsuite.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class StorageService {

    private final Path dir;

    public StorageService(@Value("${app.storage.dir}") String storageDir) {
        this.dir = Path.of(storageDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create storage dir " + dir, e);
        }
    }

    public Path pathFor(UUID docId) {
        return dir.resolve(docId.toString());
    }

    public byte[] readBytes(UUID docId) {
        try {
            return Files.readAllBytes(pathFor(docId));
        } catch (IOException e) {
            throw ApiException.notFound("File data is no longer available");
        }
    }

    public void writeBytes(UUID docId, byte[] data) {
        try {
            Files.write(pathFor(docId), data);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed writing file " + docId, e);
        }
    }

    public void delete(UUID docId) {
        try {
            Files.deleteIfExists(pathFor(docId));
        } catch (IOException e) {
            // best effort: cleanup scheduler retries expired files later
        }
    }
}
