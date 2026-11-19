package br.edu.aula.gpsmapas;

/** Um ponto geográfico com nome (linha da tabela "locais"). */
public class PontoMapa {
    public long id;            // 0 enquanto não foi gravado no banco
    public String nome;
    public final double latitude;
    public final double longitude;

    public PontoMapa(String nome, double latitude, double longitude) {
        this.nome = nome;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
