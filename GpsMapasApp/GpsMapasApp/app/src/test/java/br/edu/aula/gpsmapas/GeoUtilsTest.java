package br.edu.aula.gpsmapas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import java.util.Locale;

/** Testes unitários (rodam na JVM, sem emulador): Shift+F10 / botão direito > Run no Android Studio. */
public class GeoUtilsTest {

    private final Locale localeOriginal = Locale.getDefault();

    @After
    public void restaurarLocale() {
        Locale.setDefault(localeOriginal);
    }

    @Test
    public void distanciaCurta_pelourinhoAteFarolDaBarra() {
        // Valor conferido de forma independente (PostgreSQL e Python): 4,87 km
        double km = GeoUtils.haversineKm(-12.9714, -38.5124, -13.0105, -38.5326);
        assertEquals(4.87, km, 0.01);
    }

    @Test
    public void distanciaLonga_pelourinhoAteFeiraDeSantana() {
        double km = GeoUtils.haversineKm(-12.9714, -38.5124, -12.2664, -38.9663);
        assertEquals(92.58, km, 0.01);
    }

    @Test
    public void distanciaEntrePontoEleMesmoEZero() {
        assertEquals(0.0, GeoUtils.haversineKm(-12.9714, -38.5014, -12.9714, -38.5014), 1e-9);
    }

    @Test
    public void distanciaESimetrica() {
        double ida = GeoUtils.haversineKm(-12.9714, -38.5014, -13.0105, -38.5326);
        double volta = GeoUtils.haversineKm(-13.0105, -38.5326, -12.9714, -38.5014);
        assertEquals(ida, volta, 1e-9);
    }

    @Test
    public void coordenadasValidas() {
        assertTrue(GeoUtils.coordenadasValidas(-12.9714, -38.5014));
        assertTrue(GeoUtils.coordenadasValidas(90, 180));
        assertFalse(GeoUtils.coordenadasValidas(-138.5, 10));      // latitude impossível
        assertFalse(GeoUtils.coordenadasValidas(10, 200));         // longitude impossível
        assertFalse(GeoUtils.coordenadasValidas(Double.NaN, 0));
    }

    @Test
    public void coordenadaUsaPontoDecimalMesmoEmPortugues() {
        Locale.setDefault(Locale.forLanguageTag("pt-BR"));
        assertEquals("-12.971400", GeoUtils.formatarCoordenada(-12.9714));
    }

    @Test
    public void distanciaParaExibirUsaVirgula() {
        assertEquals("220 m", GeoUtils.formatarDistancia(0.22));
        assertEquals("5,51 km", GeoUtils.formatarDistancia(5.51));
    }

    @Test
    public void urlReversaUsaPontoDecimalMesmoEmPortugues() {
        Locale.setDefault(Locale.forLanguageTag("pt-BR"));
        assertEquals("https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=18"
                        + "&accept-language=pt-BR&lat=-12.971400&lon=-38.501400",
                NominatimUrl.reversa(-12.9714, -38.5014));
    }

    @Test
    public void urlBuscaCodificaOTexto() {
        assertEquals("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1"
                        + "&accept-language=pt-BR&q=Farol+da+Barra%2C+Salvador",
                NominatimUrl.busca("Farol da Barra, Salvador"));
    }
}
