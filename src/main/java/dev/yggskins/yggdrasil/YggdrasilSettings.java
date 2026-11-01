package dev.yggskins.yggdrasil;

/**
 * Configuracion resuelta del cliente Yggdrasil.
 */
public record YggdrasilSettings(
        String apiRoot,
        boolean requireSignature,
        boolean verifySignature,
        int timeoutSeconds,
        int cacheSeconds
) {

    public YggdrasilSettings {
        apiRoot = apiRoot == null ? "" : apiRoot.trim();
    }
}
