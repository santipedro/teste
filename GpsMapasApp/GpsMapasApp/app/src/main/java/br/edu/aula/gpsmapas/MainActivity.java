package br.edu.aula.gpsmapas;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Tela única do app de exemplo: mapa do OpenStreetMap + GPS + busca de endereço + banco SQLite.
 *
 * Roteiro de estudo (procure pelos comentários "PASSO"):
 *  1. configurar o osmdroid     2. mostrar o mapa            3. tocar no mapa
 *  4. pedir permissão de GPS    5. receber a localização     6. buscar endereço (Nominatim)
 *  7. calcular distâncias       8. gravar no SQLite
 */
public class MainActivity extends Activity implements LocationListener {

    private static final int REQ_LOCALIZACAO = 1001;
    private static final double CENTRO_LAT = -12.9714;   // Salvador - BA
    private static final double CENTRO_LNG = -38.5014;

    /** Um ponto marcado junto com o seu marcador no mapa. */
    private static class PontoNoMapa {
        final PontoMapa ponto;
        Marker marcador;

        PontoNoMapa(PontoMapa ponto) {
            this.ponto = ponto;
        }
    }

    private MapView mapa;
    private TextView tvStatus;
    private TextView tvResumo;
    private EditText etBusca;
    private Button btnAcompanhar;

    private LocationManager locationManager;
    private NominatimClient nominatim;
    private LocalDbHelper banco;

    private final List<PontoNoMapa> pontos = new ArrayList<>();
    private Polyline linha;
    private Marker marcadorGps;
    private Polygon circuloPrecisao;

    private boolean acompanhando = false;
    private boolean primeiroFix = true;
    private boolean pendenteContinuo = false;
    private Location ultimaPosicao;

    // ========================================================================================
    // Ciclo de vida
    // ========================================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // PASSO 1: configurar o osmdroid ANTES de criar o MapView.
        Configuration config = Configuration.getInstance();
        config.load(getApplicationContext(), getSharedPreferences("osmdroid", MODE_PRIVATE));
        // O OpenStreetMap exige que o app se identifique; o User-Agent padrão é bloqueado.
        config.setUserAgentValue(getPackageName() + "/1.0");
        // Cache dos tiles na pasta privada do app (não precisa de permissão de armazenamento).
        File base = new File(getCacheDir(), "osmdroid");
        config.setOsmdroidBasePath(base);
        config.setOsmdroidTileCache(new File(base, "tiles"));

        setContentView(R.layout.activity_main);
        aplicarMargensDoSistema(findViewById(R.id.raiz));

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        nominatim = new NominatimClient();
        banco = new LocalDbHelper(this);

        tvStatus = findViewById(R.id.tvStatus);
        tvResumo = findViewById(R.id.tvResumo);
        etBusca = findViewById(R.id.etBusca);
        btnAcompanhar = findViewById(R.id.btnAcompanhar);

        configurarMapa();

        findViewById(R.id.btnLocalizar).setOnClickListener(v -> iniciarLocalizacao(false));
        btnAcompanhar.setOnClickListener(v -> alternarAcompanhamento());
        findViewById(R.id.btnBuscar).setOnClickListener(v -> buscarEndereco());
        findViewById(R.id.btnSalvar).setOnClickListener(v -> salvarPontos());
        findViewById(R.id.btnVerSalvos).setOnClickListener(v -> mostrarSalvos());
        findViewById(R.id.btnLimpar).setOnClickListener(v -> limparPontos());
        etBusca.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                buscarEndereco();
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapa.onResume();
        if (acompanhando) {
            registrarListeners();       // retoma o acompanhamento que foi pausado em onPause
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        locationManager.removeUpdates(this);   // economiza bateria com o app em segundo plano
        mapa.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        nominatim.encerrar();
        banco.close();
        mapa.onDetach();
    }

    // ========================================================================================
    // PASSO 2 e 3: mapa e toques
    // ========================================================================================

    private void configurarMapa() {
        mapa = findViewById(R.id.map);
        mapa.setTileSource(TileSourceFactory.MAPNIK);      // tiles do OpenStreetMap
        mapa.setMultiTouchControls(true);                  // zoom com dois dedos
        mapa.getController().setZoom(13.0);
        mapa.getController().setCenter(new GeoPoint(CENTRO_LAT, CENTRO_LNG));

        // Atribuição obrigatória: "© OpenStreetMap contributors"
        mapa.getOverlays().add(new CopyrightOverlay(this));

        // Toque no mapa -> novo ponto. Fica na posição 0 para que os marcadores recebam os toques primeiro.
        mapa.getOverlays().add(0, new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                adicionarPonto(p.getLatitude(), p.getLongitude(), null);
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        }));

        linha = new Polyline();                            // linha ligando os pontos
        linha.getOutlinePaint().setColor(0xFF1F4E79);
        linha.getOutlinePaint().setStrokeWidth(8f);
        mapa.getOverlays().add(linha);
    }

    /**
     * Adiciona um ponto ao mapa. Se o nome vier nulo (toque no mapa), o endereço é descoberto
     * em segundo plano pela geocodificação reversa do Nominatim.
     */
    private void adicionarPonto(double lat, double lng, String nome) {
        final PontoNoMapa pn = new PontoNoMapa(new PontoMapa(
                nome != null ? nome : "Ponto " + (pontos.size() + 1), lat, lng));

        Marker m = new Marker(mapa);
        m.setPosition(new GeoPoint(lat, lng));
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        m.setTitle(pn.ponto.nome);
        m.setSnippet(GeoUtils.formatarCoordenada(lat) + ", " + GeoUtils.formatarCoordenada(lng));
        mapa.getOverlays().add(m);
        pn.marcador = m;
        pontos.add(pn);
        atualizarResumo();
        mapa.invalidate();

        if (nome == null) {
            status("Ponto marcado. Buscando o endereço...", false);
            nominatim.reversa(lat, lng, new NominatimClient.Callback<String>() {
                @Override
                public void onSucesso(String endereco) {
                    if (isFinishing() || isDestroyed()) return;
                    pn.ponto.nome = endereco;
                    pn.marcador.setTitle(endereco);
                    atualizarResumo();
                    status("Endereço: " + endereco, false);
                }

                @Override
                public void onErro(String mensagem) {
                    if (isFinishing() || isDestroyed()) return;
                    status(mensagem, true);
                }
            });
        }
    }

    // ========================================================================================
    // PASSO 7: resumo e distâncias
    // ========================================================================================

    private void atualizarResumo() {
        List<GeoPoint> trajeto = new ArrayList<>();
        StringBuilder texto = new StringBuilder();
        double total = 0;

        for (int i = 0; i < pontos.size(); i++) {
            PontoMapa p = pontos.get(i).ponto;
            trajeto.add(new GeoPoint(p.latitude, p.longitude));
            texto.append(i + 1).append(". ").append(p.nome).append('\n')
                 .append("    ").append(GeoUtils.formatarCoordenada(p.latitude)).append(", ")
                 .append(GeoUtils.formatarCoordenada(p.longitude));
            if (i > 0) {
                PontoMapa anterior = pontos.get(i - 1).ponto;
                double d = GeoUtils.haversineKm(anterior.latitude, anterior.longitude, p.latitude, p.longitude);
                total += d;
                texto.append("  (+").append(GeoUtils.formatarDistancia(d)).append(")");
            }
            texto.append('\n');
        }
        if (pontos.size() >= 2) {
            texto.append("Distância total em linha reta: ").append(GeoUtils.formatarDistancia(total));
        }

        linha.setPoints(trajeto);
        tvResumo.setText(pontos.isEmpty() ? getString(R.string.resumo_vazio) : texto.toString());
        mapa.invalidate();
    }

    private void limparPontos() {
        for (PontoNoMapa pn : pontos) {
            mapa.getOverlays().remove(pn.marcador);
        }
        pontos.clear();
        atualizarResumo();
        status("Pontos removidos do mapa.", false);
    }

    // ========================================================================================
    // PASSO 4 e 5: permissão e localização (GPS)
    // ========================================================================================

    private boolean temPermissaoFina() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean temPermissao() {
        return temPermissaoFina() || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** "Onde estou?" (uma posição) ou "Acompanhar" (várias posições). */
    private void iniciarLocalizacao(boolean continuo) {
        if (!temPermissao()) {
            // PASSO 4: pedir a permissão ao usuário (a resposta chega em onRequestPermissionsResult).
            pendenteContinuo = continuo;
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCALIZACAO);
            return;
        }
        boolean gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && temPermissaoFina();
        boolean rede = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (!gps && !rede) {
            status("Localização desligada (ou só aproximada, sem GPS). Ative a localização do aparelho.", true);
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            return;
        }
        acompanhando = continuo;
        primeiroFix = true;
        btnAcompanhar.setText(continuo ? "Parar" : getString(R.string.btn_acompanhar));
        status("Obtendo sua posição...", false);
        registrarListeners();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCALIZACAO) {
            if (temPermissao()) {
                iniciarLocalizacao(pendenteContinuo);
            } else {
                status("Permissão de localização negada. Libere nas configurações do app.", true);
            }
        }
    }

    @SuppressLint("MissingPermission")   // a permissão é verificada em iniciarLocalizacao()
    private void registrarListeners() {
        locationManager.removeUpdates(this);
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && temPermissaoFina()) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this);
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, this);
            }
        } catch (SecurityException e) {
            status("Sem permissão para acessar a localização.", true);
        }
    }

    private void alternarAcompanhamento() {
        if (acompanhando) {
            acompanhando = false;
            locationManager.removeUpdates(this);
            btnAcompanhar.setText(getString(R.string.btn_acompanhar));
            status("Acompanhamento encerrado.", false);
        } else {
            iniciarLocalizacao(true);
        }
    }

    /** PASSO 5: o Android chama este método a cada nova posição. */
    @Override
    public void onLocationChanged(Location loc) {
        ultimaPosicao = loc;
        GeoPoint gp = new GeoPoint(loc.getLatitude(), loc.getLongitude());

        if (marcadorGps == null) {
            circuloPrecisao = new Polygon();                // círculo de precisão (raio em metros)
            circuloPrecisao.getFillPaint().setColor(0x222563EB);
            circuloPrecisao.getOutlinePaint().setColor(0xFF2563EB);
            circuloPrecisao.getOutlinePaint().setStrokeWidth(2f);
            mapa.getOverlays().add(circuloPrecisao);

            GradientDrawable bolinha = new GradientDrawable();
            bolinha.setShape(GradientDrawable.OVAL);
            bolinha.setColor(0xFF2563EB);
            bolinha.setStroke(4, Color.WHITE);
            bolinha.setSize(42, 42);

            marcadorGps = new Marker(mapa);
            marcadorGps.setIcon(bolinha);
            marcadorGps.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            marcadorGps.setTitle("Você está aqui");
            mapa.getOverlays().add(marcadorGps);
        }
        marcadorGps.setPosition(gp);
        circuloPrecisao.setPoints(Polygon.pointsAsCircle(gp, loc.getAccuracy()));

        if (primeiroFix) {
            mapa.getController().setZoom(17.0);
            mapa.getController().animateTo(gp);
            primeiroFix = false;
        }
        mapa.invalidate();

        StringBuilder msg = new StringBuilder("Você está aqui (" + loc.getProvider() + "):\n")
                .append(GeoUtils.formatarCoordenada(loc.getLatitude())).append(", ")
                .append(GeoUtils.formatarCoordenada(loc.getLongitude()))
                .append("  |  precisão ±").append(Math.round(loc.getAccuracy())).append(" m");
        if (loc.hasSpeed()) {
            msg.append("  |  ").append(String.format(java.util.Locale.US, "%.1f", loc.getSpeed() * 3.6)).append(" km/h");
        }
        if (!acompanhando) {
            // Modo "uma vez": a primeira posição costuma vir da rede (imprecisa). Só paramos
            // quando ela vier do GPS ou for boa o bastante (até 50 m).
            boolean boa = LocationManager.GPS_PROVIDER.equals(loc.getProvider()) || loc.getAccuracy() <= 50;
            if (boa) {
                locationManager.removeUpdates(this);
            } else {
                msg.append("\n(aguardando o GPS para melhorar a precisão...)");
            }
        }
        status(msg.toString(), false);
    }

    // Métodos exigidos pela interface LocationListener em versões antigas do Android.
    @Override
    public void onProviderEnabled(String provider) { }

    @Override
    public void onProviderDisabled(String provider) {
        status("Provedor de localização desativado: " + provider, true);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onStatusChanged(String provider, int status, Bundle extras) { }

    // ========================================================================================
    // PASSO 6: busca de endereço
    // ========================================================================================

    private void buscarEndereco() {
        String texto = etBusca.getText().toString().trim();
        if (texto.isEmpty()) {
            status("Digite um endereço ou lugar para buscar.", true);
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(etBusca.getWindowToken(), 0);

        status("Buscando \"" + texto + "\"...", false);
        nominatim.buscar(texto, new NominatimClient.Callback<NominatimClient.Resultado>() {
            @Override
            public void onSucesso(NominatimClient.Resultado r) {
                if (isFinishing() || isDestroyed()) return;
                adicionarPonto(r.latitude, r.longitude, r.nome);
                mapa.getController().setZoom(16.0);
                mapa.getController().animateTo(new GeoPoint(r.latitude, r.longitude));
                status("Encontrado: " + r.nome, false);
            }

            @Override
            public void onErro(String mensagem) {
                if (isFinishing() || isDestroyed()) return;
                status(mensagem, true);
            }
        });
    }

    // ========================================================================================
    // PASSO 8: banco de dados SQLite
    // ========================================================================================

    private void salvarPontos() {
        if (pontos.isEmpty()) {
            status("Marque pelo menos um ponto antes de salvar.", true);
            return;
        }
        List<PontoMapa> lista = new ArrayList<>();
        for (PontoNoMapa pn : pontos) {
            if (!GeoUtils.coordenadasValidas(pn.ponto.latitude, pn.ponto.longitude)) {
                status("Coordenada inválida: " + pn.ponto.nome, true);
                return;
            }
            lista.add(pn.ponto);
        }
        try {
            int n = banco.inserirTodos(lista);
            status(n + " ponto(s) salvo(s) no banco SQLite do aparelho.", false);
        } catch (RuntimeException e) {
            status("Erro ao salvar: " + e.getMessage(), true);
        }
    }

    private void mostrarSalvos() {
        final List<PontoMapa> salvos = banco.listar();
        if (salvos.isEmpty()) {
            Toast.makeText(this, "Nenhum ponto salvo.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] itens = new String[salvos.size()];
        for (int i = 0; i < itens.length; i++) {
            PontoMapa p = salvos.get(i);
            itens[i] = p.id + ". " + p.nome + "\n" + GeoUtils.formatarCoordenada(p.latitude)
                    + ", " + GeoUtils.formatarCoordenada(p.longitude);
        }
        new AlertDialog.Builder(this)
                .setTitle("Pontos salvos (" + salvos.size() + ")")
                .setItems(itens, null)
                .setPositiveButton("Fechar", null)
                .setNeutralButton("Apagar todos", (dialogo, qual) -> {
                    int apagados = banco.apagarTodos();
                    status(apagados + " ponto(s) apagado(s) do banco.", false);
                })
                .show();
    }

    // ========================================================================================
    // Utilidades de interface
    // ========================================================================================

    private void status(String mensagem, boolean erro) {
        tvStatus.setText(mensagem);
        tvStatus.setTextColor(erro ? 0xFFB42318 : 0xFF1D2733);
    }

    /**
     * No Android 15+ (targetSdk 35 ou mais) o app é desenhado "de ponta a ponta", por baixo das
     * barras do sistema. Este código afasta o conteúdo da barra de status e da barra de navegação.
     */
    private void aplicarMargensDoSistema(View raiz) {
        raiz.setOnApplyWindowInsetsListener((v, insets) -> {
            int topo;
            int base;
            if (Build.VERSION.SDK_INT >= 30) {
                // ime(): reserva espaço para o teclado, que não é uma "barra do sistema".
                android.graphics.Insets barras = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                topo = barras.top;
                base = barras.bottom;
                v.setPadding(barras.left, topo, barras.right, base);
                return WindowInsets.CONSUMED;
            }
            topo = insets.getSystemWindowInsetTop();
            base = insets.getSystemWindowInsetBottom();
            v.setPadding(insets.getSystemWindowInsetLeft(), topo,
                    insets.getSystemWindowInsetRight(), base);
            return insets.consumeSystemWindowInsets();
        });
    }
}
