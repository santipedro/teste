package br.edu.aula.gpsmapas;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;

/** Monta as URLs do Nominatim (geocodificação do OpenStreetMap). Java puro, testável. */
public final class NominatimUrl {

    public static final String BASE = "https://nominatim.openstreetmap.org";

    private NominatimUrl() { }

    /** Endereço -> coordenadas. */
    public static String busca(String texto) {
        return BASE + "/search?format=jsonv2&limit=1&accept-language=pt-BR&q=" + codificar(texto);
    }

    /** Coordenadas -> endereço. O Nominatim usa "lon" (e não "lng"). */
    public static String reversa(double latitude, double longitude) {
        // Locale.US: garante PONTO decimal na URL, mesmo em aparelhos configurados em português.
        return String.format(Locale.US,
                "%s/reverse?format=jsonv2&zoom=18&accept-language=pt-BR&lat=%.6f&lon=%.6f",
                BASE, latitude, longitude);
    }

    private static String codificar(String texto) {
        try {
            return URLEncoder.encode(texto, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);   // UTF-8 sempre existe
        }
    }
}
