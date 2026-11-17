package tn.elitetunisia.common.exception;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tn.elitetunisia.auth.service.InvalidRefreshTokenException;
import tn.elitetunisia.catalogue.CatalogueConflictException;
import tn.elitetunisia.catalogue.CatalogueNotFoundException;
import tn.elitetunisia.catalogue.dto.InvalidCatalogueRequestException;
import tn.elitetunisia.common.storage.FileStorageNotFoundException;
import tn.elitetunisia.common.storage.InvalidImageUploadException;
import tn.elitetunisia.user.service.InvalidUserRoleException;
import tn.elitetunisia.user.service.UserNotFoundException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(tn.elitetunisia.payment.PaymentException.class)
    ResponseEntity<ProblemDetail> handlePayment(tn.elitetunisia.payment.PaymentException exception) {
        HttpStatus status = switch (exception.getCode()) {
            case "QUOTE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "INVALID_PAYMENT" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.CONFLICT;
        };
        ProblemDetail response = problem(status, exception.getMessage());
        response.setProperty("code", exception.getCode());
        return ResponseEntity.status(status).body(response);
    }
    @ExceptionHandler(tn.elitetunisia.quote.QuoteException.class)
    ResponseEntity<ProblemDetail> handleQuote(tn.elitetunisia.quote.QuoteException exception) {
        HttpStatus status = exception.getCode().equals("QUOTE_NOT_FOUND") ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT;
        ProblemDetail response = problem(status, exception.getMessage()); response.setProperty("code", exception.getCode());
        return ResponseEntity.status(status).body(response);
    }
    @ExceptionHandler(tn.elitetunisia.booking.BookingException.class)
    ResponseEntity<ProblemDetail> handleBooking(tn.elitetunisia.booking.BookingException exception) {
        HttpStatus status = switch (exception.getCode()) {
            case "BOOKING_NOT_FOUND", "VEHICLE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "BOOKING_ACCESS_DENIED" -> HttpStatus.FORBIDDEN;
            case "INVALID_DATE_RANGE" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.CONFLICT;
        };
        ProblemDetail response = problem(status, exception.getMessage());
        response.setProperty("code", exception.getCode());
        return ResponseEntity.status(status).body(response);
    }
    @ExceptionHandler(tn.elitetunisia.common.exception.DomainException.class)
    ResponseEntity<ProblemDetail> handleDomain(DomainException exception) {
        ProblemDetail response = problem(exception.getStatus(), DOMAIN_MESSAGES.getOrDefault(exception.getCode(), "La demande ne peut pas être traitée."));
        response.setProperty("code", exception.getCode());
        var builder = ResponseEntity.status(exception.getStatus());
        if (exception instanceof TooManyLoginAttemptsException throttled) {
            builder.header(org.springframework.http.HttpHeaders.RETRY_AFTER, String.valueOf(throttled.getRetryAfterSeconds()));
        }
        return builder.body(response);
    }

    private static final Map<String, String> DOMAIN_MESSAGES = Map.ofEntries(
            Map.entry("BOOKING_NOT_FOUND", "Réservation introuvable."),
            Map.entry("DRIVER_NOT_FOUND", "Chauffeur introuvable."),
            Map.entry("ASSIGNMENT_NOT_FOUND", "Aucun chauffeur n'est affecté à cette réservation."),
            Map.entry("INVOICE_NOT_FOUND", "Facture introuvable."),
            Map.entry("QUOTE_NOT_FOUND", "Devis introuvable."),
            Map.entry("QUOTE_NOT_ACCEPTED", "Le devis doit être accepté avant d'être facturé."),
            Map.entry("PAYMENT_NOT_FOUND", "Paiement introuvable."),
            Map.entry("PAYMENT_NOT_CAPTURED", "Seul un paiement encaissé peut être rattaché à une facture."),
            Map.entry("LOGIN_THROTTLED", "Trop de tentatives de connexion. Réessayez dans quelques minutes."),
            Map.entry("LAST_SUPER_ADMIN", "Le dernier super administrateur actif ne peut être ni désactivé ni rétrogradé."),
            Map.entry("TRANSFER_NOT_FOUND", "Transfert introuvable."),
            Map.entry("SERVICE_NOT_FOUND", "Service introuvable."),
            Map.entry("BOOKING_STATUS_NO_DRIVER", "Un chauffeur ne peut être affecté qu'à une réservation en attente ou confirmée."),
            Map.entry("DRIVER_INACTIVE", "Ce chauffeur est désactivé."),
            Map.entry("DRIVER_LICENSE_EXPIRED", "Le permis de ce chauffeur est expiré."),
            Map.entry("DRIVER_NOT_CERTIFIED", "Ce chauffeur n'est pas certifié."),
            Map.entry("DRIVER_NOT_AVAILABLE", "Ce chauffeur n'est pas disponible sur cette période."),
            Map.entry("GUARANTEE_OVERLAP", "Une garantie active chevauche déjà cette période pour ce véhicule."),
            Map.entry("TRANSFER_BOOKING_STATUS", "Un transfert ne peut être planifié que pour une réservation en attente ou confirmée."),
            Map.entry("TRANSFER_OUTSIDE_BOOKING_PERIOD", "La prise en charge doit se situer pendant la période de la réservation."),
            Map.entry("TRANSFER_TOO_MANY_PASSENGERS", "Le nombre de passagers dépasse la capacité du véhicule."),
            Map.entry("TRANSFER_INVALID_STATUS_TRANSITION", "Ce transfert ne peut pas changer vers cet état."),
            Map.entry("TRANSFER_NOT_EDITABLE", "Ce transfert n'est plus modifiable."),
            Map.entry("TRANSFER_LEGACY_BOOKING_REQUIRED", "Ce transfert n'est rattaché à aucune réservation."),
            Map.entry("TRANSFER_DRIVER_MISMATCH", "Cette réservation a déjà un autre chauffeur affecté."),
            Map.entry("SERVICE_NAME_TAKEN", "Un service actif porte déjà ce nom dans cette devise."),
            Map.entry("SERVICE_NAME_TOO_SHORT", "Le nom du service doit contenir au moins 2 caractères."),
            Map.entry("VEHICLE_HAS_ACTIVE_BOOKING", "Ce véhicule a une réservation confirmée ou en cours : il ne peut pas être retiré ni mis en maintenance pour cette période."),
            Map.entry("VEHICLE_HAS_BOOKINGS", "Ce véhicule possède un historique de réservations et ne peut pas être supprimé. Retirez-le du catalogue à la place."));

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Les données envoyées sont invalides.");
        Map<String, String> errors = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(error -> error.getField(), error -> error.getDefaultMessage(), (first, second) -> first));
        problem.setProperty("fieldErrors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation() {
        return ResponseEntity.badRequest()
                .body(problem(HttpStatus.BAD_REQUEST, "Les paramètres envoyés sont invalides."));
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ResponseEntity<ProblemDetail> handleDuplicate(EmailAlreadyRegisteredException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem(HttpStatus.CONFLICT, exception.getMessage()));
    }

    @ExceptionHandler({AuthenticationException.class, InvalidRefreshTokenException.class})
    ResponseEntity<ProblemDetail> handleAuthentication(RuntimeException exception) {
        String detail = exception instanceof InvalidRefreshTokenException
                ? exception.getMessage()
                : "Adresse e-mail ou mot de passe incorrect.";
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem(HttpStatus.UNAUTHORIZED, detail));
    }

    @ExceptionHandler(DisabledException.class)
    ResponseEntity<ProblemDetail> handleDisabledAccount(DisabledException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(problem(HttpStatus.UNAUTHORIZED, "Votre compte a été désactivé."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(problem(HttpStatus.FORBIDDEN, "Vous n'avez pas l'autorisation d'effectuer cette action."));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, InvalidUserRoleException.class})
    ResponseEntity<ProblemDetail> handleBadRequest(RuntimeException exception) {
        String detail = exception instanceof InvalidUserRoleException
                ? exception.getMessage()
                : "Le format des données envoyées est invalide.";
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, detail));
    }

    @ExceptionHandler({InvalidCatalogueRequestException.class, InvalidImageUploadException.class})
    ResponseEntity<ProblemDetail> handleCatalogueBadRequest(RuntimeException exception) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, exception.getMessage()));
    }

    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.multipart.MultipartException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    ResponseEntity<ProblemDetail> handleInvalidParameter(Exception exception) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "Le paramètre ou fichier envoyé est invalide."));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> handleUploadTooLarge(Exception exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(problem(HttpStatus.PAYLOAD_TOO_LARGE, "Le fichier dépasse la taille maximale autorisée."));
    }

    @ExceptionHandler(CatalogueConflictException.class)
    ResponseEntity<ProblemDetail> handleCatalogueConflict(CatalogueConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem(HttpStatus.CONFLICT, exception.getMessage()));
    }

    @ExceptionHandler({NoResourceFoundException.class, UserNotFoundException.class, CatalogueNotFoundException.class, FileStorageNotFoundException.class})
    ResponseEntity<ProblemDetail> handleNotFound(Exception exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem(HttpStatus.NOT_FOUND, "La ressource demandée est introuvable."));
    }

    /**
     * Classe les violations PostgreSQL par source. Le message SQL brut n'est jamais renvoyé au client :
     * seul le code métier explicite l'est.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleConflict(DataIntegrityViolationException exception) {
        java.sql.SQLException sql = sqlCause(exception);
        String state = sql == null ? null : sql.getSQLState();
        String message = sql == null || sql.getMessage() == null ? "" : sql.getMessage().toLowerCase(java.util.Locale.ROOT);
        if ("23P01".equals(state)) {
            if (message.contains("garantie chevauchante")) {
                return handleDomain(DomainException.conflict("GUARANTEE_OVERLAP"));
            }
            if (message.contains("période déjà bloquée") || message.contains("bookings_no_blocking_overlap")) {
                return handleBooking(new tn.elitetunisia.booking.BookingException("VEHICLE_NOT_AVAILABLE", "Ce véhicule est déjà réservé pour cette période."));
            }
        }
        if ("23514".equals(state) && message.contains("aucune garantie active")) {
            return handleBooking(new tn.elitetunisia.booking.BookingException("VEHICLE_NOT_AVAILABLE", "La garantie de cette réservation ne couvre plus la période demandée."));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem(HttpStatus.CONFLICT, "Cette demande entre en conflit avec des données existantes."));
    }

    private static java.sql.SQLException sqlCause(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLException sql && sql.getSQLState() != null) return sql;
        }
        return null;
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> handleMethodNotAllowed(Exception exception) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(problem(HttpStatus.METHOD_NOT_ALLOWED, "Méthode HTTP non autorisée pour cette ressource."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, WebRequest request) {
        String path = request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI()
                : "unknown";
        logger.error("Unhandled API exception on {} ({}).", path, exception.getClass().getName());
        logger.debug("Unhandled API exception details.", exception);
        return ResponseEntity.internalServerError().body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur est survenue. Veuillez réessayer."));
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("about:blank"));
        return problem;
    }
}