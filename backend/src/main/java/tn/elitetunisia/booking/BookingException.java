package tn.elitetunisia.booking;
public class BookingException extends RuntimeException {
    private final String code;
    public BookingException(String code, String message) { super(message); this.code = code; }
    public String getCode() { return code; }
}