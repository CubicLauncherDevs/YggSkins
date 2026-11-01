package dev.yggskins.provider;

/**
 * Error de un proveedor con una clave de mensaje traducible.
 */
public class SkinProviderException extends Exception {

    private final String messageKey;
    private final String detail;

    public SkinProviderException(String messageKey, String detail) {
        super(messageKey + (detail == null ? "" : ": " + detail));
        this.messageKey = messageKey;
        this.detail = detail;
    }

    public SkinProviderException(String messageKey, String detail, Throwable cause) {
        super(messageKey + (detail == null ? "" : ": " + detail), cause);
        this.messageKey = messageKey;
        this.detail = detail;
    }

    public String messageKey() {
        return messageKey;
    }

    public String detail() {
        return detail;
    }
}
