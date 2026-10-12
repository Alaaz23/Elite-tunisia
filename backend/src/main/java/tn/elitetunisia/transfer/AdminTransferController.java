package tn.elitetunisia.transfer;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/transfers")
public class AdminTransferController {
    private final TransferService service;

    public AdminTransferController(TransferService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<TransferEntity> create(@RequestBody TransferEntity t) { return ResponseEntity.ok(service.create(t)); }

    @GetMapping
    public ResponseEntity<List<TransferEntity>> list() { return ResponseEntity.ok(service.listAll()); }

    @GetMapping("/{id}")
    public ResponseEntity<TransferEntity> get(@PathVariable UUID id) { return ResponseEntity.ok(service.get(id)); }
}
