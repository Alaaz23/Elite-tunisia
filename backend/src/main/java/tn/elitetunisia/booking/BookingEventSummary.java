package tn.elitetunisia.booking;
import java.time.Instant;
import java.util.UUID;
public record BookingEventSummary(UUID actorId, String action, BookingStatus fromStatus, BookingStatus toStatus, Instant timestamp) { }