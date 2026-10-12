package tn.elitetunisia.common.storage;

import java.io.IOException;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tn.elitetunisia.brand.BrandRepository;
import tn.elitetunisia.vehicle.VehicleImageRepository;
import tn.elitetunisia.catalogue.CatalogueConflictException;

@RestController
@RequestMapping("/api/admin/uploads")
@PreAuthorize("hasAnyAuthority('vehicles:manage', 'brands:manage')")
public class ImageUploadController {
    private final FileStorageService storage;
    private final VehicleImageRepository images;
    private final BrandRepository brands;
    public ImageUploadController(FileStorageService storage, VehicleImageRepository images, BrandRepository brands) {
        this.storage = storage;
        this.images = images;
        this.brands = brands;
    }

    @PostMapping("/images")
    public Map<String, String> upload(@RequestPart("file") MultipartFile file) throws IOException {
        return Map.of("url", storage.storeImage(file));
    }

    @DeleteMapping("/images/{key:.+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String key) throws IOException {
        String url = "/api/files/" + key;
        if (images.existsByUrl(url) || brands.existsByLogoUrl(url)) {
            throw new CatalogueConflictException("Cette image est encore utilisée dans le catalogue.");
        }
        storage.deleteUrl(url);
    }
}