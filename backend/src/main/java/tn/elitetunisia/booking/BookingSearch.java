package tn.elitetunisia.booking;
import java.time.Instant;
import java.util.UUID;
public record BookingSearch(String reference, UUID customer, UUID vehicle, BookingStatus status, Instant startDate, Instant endDate, Integer page, Integer size) { }