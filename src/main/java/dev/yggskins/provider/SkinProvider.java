package dev.yggskins.provider;

import dev.yggskins.skin.SkinData;
import dev.yggskins.skin.SkinVariant;

/**
 * Fuente de skins. Las implementaciones son bloqueantes y se ejecutan
 * sobre hilos virtuales.
 */
public interface SkinProvider {

    String name();

    boolean available();

    /**
     * @param key     nombre de jugador, UUID, URL o nombre de skin personalizada.
     * @param variant variante solicitada, o null para autodetectarla.
     * @return la skin encontrada, o null si el origen no la conoce.
     */
    SkinData fetch(String key, SkinVariant variant) throws SkinProviderException;
}
