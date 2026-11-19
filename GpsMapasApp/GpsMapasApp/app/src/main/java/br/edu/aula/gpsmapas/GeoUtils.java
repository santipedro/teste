package br.edu.aula.gpsmapas;

import java.util.Locale;

/**
 * Funções geográficas em Java puro (não dependem do Android, por isso são fáceis de testar).
 */
public final class GeoUtils {

    /** Raio médio da Terra, em km. */
    private static final double RAIO_TERRA_KM = 6371.0088;

    private GeoUtils() { }

    /**
     * Distância em linha reta entre duas coordenadas, em km (fórmula de Haversine).
     * Alternativa pronta do Android: {@code android.location.Location.distanceBetween(...)}.
     */
    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * RAIO_TERRA_KM * Math.asin(Math.sqrt(h));
    }

    /** Latitude entre -90 e 90 e longitude entre -180 e 180 (a mesma regra do CHECK do banco). */
    public static boolean coordenadasValidas(double lat, double lng) {
        return !Double.isNaN(lat) && !Double.isNaN(lng)
                && lat >= -90 && lat <= 90
                && lng >= -180 && lng <= 180;
    }

    /**
     * Coordenada com 6 casas decimais e PONTO decimal, independente do idioma do aparelho.
     * Sem o Locale.US, um celular em português produziria "-12,971400" (com vírgula).
     */
    public static String formatarCoordenada(double valor) {
        return String.format(Locale.US, "%.6f", valor);
    }

    /** Distância para exibir ao usuário: "220 m" ou "5,51 km". */
    public static String formatarDistancia(double km) {
        if (km < 1) {
            return Math.round(km * 1000) + " m";
        }
        return String.format(Locale.forLanguageTag("pt-BR"), "%.2f km", km);
    }
}
