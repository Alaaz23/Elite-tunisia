package tn.elitetunisia.transfer;

import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import tn.elitetunisia.catalogue.dto.PagedResponse;
import tn.elitetunisia.user.service.AccountService;

/**
 * Transferts rattachés à une réservation, réservés au personnel qui gère les réservations.
 * Aucune entité JPA n'est exposée : requêtes et réponses passent par des DTO à champs limités.
 */
@RestController
@RequestMapping("/api/admin/transfers")
@PreAuthorize("hasAuthority('bookings:manage')")
@Validated
public class AdminTransferController {
    private final TransferService service;
    private final AccountService accounts;

    public AdminTransferController(TransferService service, AccountService accounts) { this.service = service; this.accounts = accounts; }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse create(@Valid @RequestBody TransferRequest request, Authentication auth, HttpServletRequest servlet) {
        return TransferResponse.from(service.create(request, actor(auth), servlet.getRemoteAddr()));
    }

    @GetMapping
    public PagedResponse<TransferResponse> list(@RequestParam(required = false) UUID bookingId,
            @RequestParam(required = false) TransferStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        var result = service.list(bookingId, status, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "pickupAt")));
        return new PagedResponse<>(result.map(TransferResponse::from).getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
    }

    @GetMapping("/{id}")
    public TransferResponse get(@PathVariable UUID id) { return TransferResponse.from(service.get(id)); }

    @PutMapping("/{id}")
    public TransferResponse update(@PathVariable UUID id, @Valid @RequestBody TransferUpdateRequest request, Authentication auth, HttpServletRequest servlet) {
        return TransferResponse.from(service.update(id, request, actor(auth), servlet.getRemoteAddr()));
    }

    @PostMapping("/{id}/confirm")
    public TransferResponse confirm(@PathVariable UUID id, Authentication auth, HttpServletRequest servlet) { return move(id, TransferStatus.CONFIRMED, auth, servlet); }

    @PostMapping("/{id}/complete")
    public TransferResponse complete(@PathVariable UUID id, Authentication auth, HttpServletRequest servlet) { return move(id, TransferStatus.COMPLETED, auth, servlet); }

    @PostMapping("/{id}/cancel")
    public TransferResponse cancel(@PathVariable UUID id, Authentication auth, HttpServletRequest servlet) { return move(id, TransferStatus.CANCELLED, auth, servlet); }

    private TransferResponse move(UUID id, TransferStatus target, Authentication auth, HttpServletRequest servlet) {
        return TransferResponse.from(service.transition(id, target, actor(auth), servlet.getRemoteAddr()));
    }

    private UUID actor(Authentication auth) { return accounts.current(auth.getName()).id(); }
}
