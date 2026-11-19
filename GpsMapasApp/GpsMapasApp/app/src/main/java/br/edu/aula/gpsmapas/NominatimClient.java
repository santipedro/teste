package br.edu.aula.gpsmapas;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cliente simples do Nominatim (geocodificação do OpenStreetMap).
 *
 * Regras da política de uso do serviço público, respeitadas aqui:
 *  - no máximo 1 requisição por segundo (todas passam por UMA fila e esperam 1,1 s entre si);
 *  - identificação da aplicação por um User-Agent próprio (troque o contato abaixo!);
 *  - sem autocompletar a cada tecla: a busca só acontece quando o usuário confirma.
 *
 * Rede nunca pode ser feita na thread principal; por isso usamos um ExecutorService e
 * devolvemos o resultado na thread principal com um Handler.
 */
public class NominatimClient {

    public interface Callback<T> {
        void onSucesso(T resultado);
        void onErro(String mensagem);
    }

    public static class Resultado {
        public final double latitude;
        public final double longitude;
        public final String nome;

        Resultado(double latitude, double longitude, String nome) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.nome = nome;
        }
    }

    // TODO (professor): troque pelo nome do seu app e um contato real.
    private static final String USER_AGENT =
            "GpsMapasAula/1.0 (app de exemplo para aula; contato: professor@exemplo.com.br)";
    private static final long INTERVALO_MINIMO_MS = 1100;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler principal = new Handler(Looper.getMainLooper());
    private long ultimaChamada = 0;   // usado apenas dentro da thread do executor

    /** Endereço -> coordenadas (primeiro resultado). */
    public void buscar(final String texto, final Callback<Resultado> callback) {
        executor.execute(() -> {
            try {
                JSONArray lista = new JSONArray(get(NominatimUrl.busca(texto)));
                if (lista.length() == 0) {
                    erro(callback, "Nenhum resultado para \"" + texto + "\".");
                    return;
                }
                JSONObject o = lista.getJSONObject(0);
                // lat e lon chegam como TEXTO no JSON
                final Resultado r = new Resultado(
                        Double.parseDouble(o.getString("lat")),
                        Double.parseDouble(o.getString("lon")),
                        encurtar(o.getString("display_name")));
                principal.post(() -> callback.onSucesso(r));
            } catch (Exception e) {
                erro(callback, "Falha na busca: " + e.getMessage());
            }
        });
    }

    /** Coordenadas -> endereço. */
    public void reversa(final double latitude, final double longitude, final Callback<String> callback) {
        executor.execute(() -> {
            try {
                JSONObject o = new JSONObject(get(NominatimUrl.reversa(latitude, longitude)));
                final String nome = o.optString("display_name", "");
                if (nome.isEmpty()) {
                    erro(callback, "Sem endereço para este ponto.");
                    return;
                }
                final String curto = encurtar(nome);
                principal.post(() -> callback.onSucesso(curto));
            } catch (Exception e) {
                erro(callback, "Falha ao obter o endereço: " + e.getMessage());
            }
        });
    }

    /** Chame em onDestroy() para encerrar a fila. */
    public void encerrar() {
        executor.shutdownNow();
    }

    // ---------------------------------------------------------------------------------------

    private String get(String endereco) throws IOException, InterruptedException {
        long espera = ultimaChamada + INTERVALO_MINIMO_MS - SystemClock.elapsedRealtime();
        if (espera > 0) {
            Thread.sleep(espera);                       // respeita o limite de 1 requisição/s
        }
        ultimaChamada = SystemClock.elapsedRealtime();

        HttpURLConnection conexao = (HttpURLConnection) URI.create(endereco).toURL().openConnection();
        try {
            conexao.setRequestProperty("User-Agent", USER_AGENT);
            conexao.setConnectTimeout(10000);
            conexao.setReadTimeout(15000);
            int codigo = conexao.getResponseCode();
            if (codigo != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + codigo);
            }
            StringBuilder texto = new StringBuilder();
            try (BufferedReader leitor = new BufferedReader(
                    new InputStreamReader(conexao.getInputStream(), StandardCharsets.UTF_8))) {
                String linha;
                while ((linha = leitor.readLine()) != null) {
                    texto.append(linha);
                }
            }
            return texto.toString();
        } finally {
            conexao.disconnect();
        }
    }

    private <T> void erro(final Callback<T> callback, final String mensagem) {
        principal.post(() -> callback.onErro(mensagem));
    }

    /** Mantém só as 3 primeiras partes do endereço ("Rua, Bairro, Cidade"). */
    private static String encurtar(String nomeCompleto) {
        String[] partes = nomeCompleto.split(",");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, partes.length); i++) {
            if (i > 0) sb.append(", ");
            sb.append(partes[i].trim());
        }
        return sb.toString();
    }
}
