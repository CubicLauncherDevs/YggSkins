package dev.yggskins.storage;

/**
 * Error no recuperable de la capa de almacenamiento.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public StorageException(String message) {
        super(message);
    }
}
