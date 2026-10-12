package tn.elitetunisia.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalFileStorageService implements FileStorageService {
    private static final Map<String, String> IMAGE_EXTENSIONS = Map.of(
            "image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp");
    private static final String PUBLIC_PREFIX = "/api/files/";
    private final Path root;
    private final long maxImageSizeBytes;

    public LocalFileStorageService(@Value("${app.storage.local-path}") String root,
            @Value("${app.storage.max-image-size-bytes}") long maxImageSizeBytes) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.maxImageSizeBytes = maxImageSizeBytes;
    }

    @Override
    public String storeImage(MultipartFile image) throws IOException {
        if (image == null || image.isEmpty()) throw new InvalidImageUploadException("Choisissez une image.");
        if (image.getSize() > maxImageSizeBytes) throw new InvalidImageUploadException("L'image dépasse la taille maximale autorisée.");
        String extension = IMAGE_EXTENSIONS.get(image.getContentType());
        if (extension == null) throw new InvalidImageUploadException("Formats acceptés : JPEG, PNG et WebP.");
        try (InputStream input = image.getInputStream()) {
            if (!matchesSignature(image.getContentType(), input.readNBytes(12))) {
                throw new InvalidImageUploadException("Le contenu du fichier ne correspond pas à son format déclaré.");
            }
        }

        Files.createDirectories(root);
        String key = UUID.randomUUID() + extension;
        Path target = safePath(key);
        try (var input = image.getInputStream()) {
            Files.copy(input, target);
        }
        return PUBLIC_PREFIX + key;
    }

    @Override
    public Resource load(String key) {
        Path path = safePath(key);
        if (!Files.isRegularFile(path)) throw new FileStorageNotFoundException();
        return new FileSystemResource(path);
    }

    @Override
    public void deleteUrl(String url) throws IOException {
        if (url == null || !url.startsWith(PUBLIC_PREFIX)) return;
        Path path = safePath(url.substring(PUBLIC_PREFIX.length()));
        Files.deleteIfExists(path);
    }

    private Path safePath(String key) {
        if (key == null || !key.matches("[A-Za-z0-9-]+\\.(jpg|png|webp)")) {
            throw new InvalidImageUploadException("Chemin d'image invalide.");
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) throw new InvalidImageUploadException("Chemin d'image invalide.");
        return path;
    }

    private boolean matchesSignature(String contentType, byte[] bytes) {
        return switch (contentType) {
            case "image/jpeg" -> bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
            case "image/png" -> bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P'
                    && bytes[2] == 'N' && bytes[3] == 'G' && (bytes[4] & 0xff) == 0x0d
                    && (bytes[5] & 0xff) == 0x0a && (bytes[6] & 0xff) == 0x1a && (bytes[7] & 0xff) == 0x0a;
            case "image/webp" -> bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I'
                    && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E'
                    && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
    }
}