package dev.yggskins.yggdrasil;

import dev.yggskins.skin.SkinProperty;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Verifica propiedades "textures" firmadas con SHA1withRSA.
 */
public final class SignatureVerifier {

    private SignatureVerifier() {
    }

    public static PublicKey parsePublicKey(String pem) {
        if (pem == null || pem.isBlank()) {
            return null;
        }
        String normalized = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        try {
            byte[] decoded = Base64.getDecoder().decode(normalized);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean verify(SkinProperty property, PublicKey publicKey) {
        if (property == null || publicKey == null || !property.signed()) {
            return false;
        }
        try {
            Signature signature = Signature.getInstance("SHA1withRSA");
            signature.initVerify(publicKey);
            signature.update(property.value().getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(property.signature().trim()));
        } catch (Exception e) {
            return false;
        }
    }
}
