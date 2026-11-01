package dev.yggskins.yggdrasil;

/**
 * Metadatos publicados por el servidor de autenticacion en su API Root.
 */
public record YggdrasilMetadata(String apiRoot, String signaturePublicKey, String serverName) {
}
