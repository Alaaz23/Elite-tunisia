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
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Les données envoyées sont invalides.");
        Map<String, String> errors = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(error -> error.getField(), error -> error.getDefaultMessage(), (first, second) -> first));
        problem.setProperty("fieldErrors", errors);
        return ResponseEntity.badRequest().body(problem);
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

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleConflict(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof java.sql.SQLException sql && "23P01".equals(sql.getSQLState())) {
                return handleBooking(new tn.elitetunisia.booking.BookingException("VEHICLE_NOT_AVAILABLE", "Ce véhicule est déjà réservé pour cette période."));
            }
            cause = cause.getCause();
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem(HttpStatus.CONFLICT, "Cette demande entre en conflit avec des données existantes."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        return ResponseEntity.internalServerError().body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur est survenue. Veuillez réessayer."));
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("about:blank"));
        return problem;
    }
}