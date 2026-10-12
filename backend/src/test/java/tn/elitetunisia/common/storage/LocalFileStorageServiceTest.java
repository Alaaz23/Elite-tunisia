package tn.elitetunisia.common.storage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

class LocalFileStorageServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void storesAndDeletesImageWithGeneratedKey() throws IOException {
        LocalFileStorageService storage = storage(1024);
        MockMultipartFile image = new MockMultipartFile("file", "car.png", "image/png", pngSignature());

        String url = storage.storeImage(image);
        String key = url.substring("/api/files/".length());
        Resource saved = storage.load(key);

        assertTrue(key.matches("[a-f0-9-]{36}\\.png"));
        assertTrue(saved.exists());
        storage.deleteUrl(url);
        assertFalse(saved.exists());
    }

    @Test
    void rejectsAnImageWhoseSignatureDoesNotMatchItsDeclaredType() {
        LocalFileStorageService storage = storage(1024);
        MockMultipartFile image = new MockMultipartFile("file", "car.png", "image/png", new byte[] { (byte) 0xff, (byte) 0xd8, (byte) 0xff });

        assertThrows(InvalidImageUploadException.class, () -> storage.storeImage(image));
    }

    @Test
    void enforcesImageSizeLimitAndRejectsArbitraryPaths() {
        LocalFileStorageService storage = storage(7);
        MockMultipartFile image = new MockMultipartFile("file", "car.png", "image/png", pngSignature());

        assertThrows(InvalidImageUploadException.class, () -> storage.storeImage(image));
        assertThrows(InvalidImageUploadException.class, () -> storage.load("../secret.png"));
    }

    private LocalFileStorageService storage(long maxSize) {
        return new LocalFileStorageService(temporaryDirectory.toString(), maxSize);
    }

    private byte[] pngSignature() {
        return new byte[] { (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a };
    }
}