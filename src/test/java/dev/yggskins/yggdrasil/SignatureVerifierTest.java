package dev.yggskins.yggdrasil;

import dev.yggskins.skin.SkinProperty;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignatureVerifierTest {

    private static final String VALUE = Base64.getEncoder().encodeToString(
            "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/abc\"}}}"
                    .getBytes(StandardCharsets.UTF_8));

    @Test
    void verifiesValidSignature() throws Exception {
        KeyPair pair = generate();
        SkinProperty property = new SkinProperty("textures", VALUE, sign(VALUE, pair));
        PublicKey key = SignatureVerifier.parsePublicKey(toPem(pair.getPublic()));
        assertNotNull(key);
        assertTrue(SignatureVerifier.verify(property, key));
    }

    @Test
    void rejectsTamperedValue() throws Exception {
        KeyPair pair = generate();
        SkinProperty property = new SkinProperty("textures", VALUE + "x", sign(VALUE, pair));
        assertFalse(SignatureVerifier.verify(property, SignatureVerifier.parsePublicKey(toPem(pair.getPublic()))));
    }

    @Test
    void rejectsSignatureFromAnotherKey() throws Exception {
        KeyPair signer = generate();
        KeyPair other = generate();
        SkinProperty property = new SkinProperty("textures", VALUE, sign(VALUE, signer));
        assertFalse(SignatureVerifier.verify(property, SignatureVerifier.parsePublicKey(toPem(other.getPublic()))));
    }

    @Test
    void rejectsUnsignedProperty() throws Exception {
        KeyPair pair = generate();
        SkinProperty property = new SkinProperty("textures", VALUE, null);
        assertFalse(SignatureVerifier.verify(property, SignatureVerifier.parsePublicKey(toPem(pair.getPublic()))));
    }

    static KeyPair generate() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    static String sign(String value, KeyPair pair) throws Exception {
        Signature signature = Signature.getInstance("SHA1withRSA");
        signature.initSign(pair.getPrivate());
        signature.update(value.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    static String toPem(PublicKey key) {
        return "-----BEGIN PUBLIC KEY-----\n" + Base64.getEncoder().encodeToString(key.getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
    }
}
