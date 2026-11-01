package dev.yggskins.yggdrasil;

/**
 * Error al comunicarse con el servidor de autenticacion.
 */
public class YggdrasilException extends Exception {

    private final String reason;

    public YggdrasilException(String reason) {
        super(reason);
        this.reason = reason;
    }

    public YggdrasilException(String reason, Throwable cause) {
        super(reason, cause);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
