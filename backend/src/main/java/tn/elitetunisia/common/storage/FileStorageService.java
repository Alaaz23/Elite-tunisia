package tn.elitetunisia.common.storage;

import java.io.IOException;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    String storeImage(MultipartFile image) throws IOException;
    Resource load(String key);
    void deleteUrl(String url) throws IOException;
}