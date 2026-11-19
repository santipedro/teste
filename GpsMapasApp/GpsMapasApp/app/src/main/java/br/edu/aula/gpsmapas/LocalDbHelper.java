package br.edu.aula.gpsmapas;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Banco SQLite local do aparelho. É o equivalente, no celular, da tabela "locais" do PostgreSQL.
 * Os valores são passados por ContentValues (parâmetros), nunca concatenados no SQL.
 */
public class LocalDbHelper extends SQLiteOpenHelper {

    private static final String NOME_BANCO = "aula_gps.db";
    private static final int VERSAO = 1;
    private static final String TABELA = "locais";

    public LocalDbHelper(Context contexto) {
        super(contexto, NOME_BANCO, null, VERSAO);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABELA + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "nome TEXT NOT NULL, "
                + "latitude REAL NOT NULL CHECK (latitude BETWEEN -90 AND 90), "
                + "longitude REAL NOT NULL CHECK (longitude BETWEEN -180 AND 180), "
                + "criado_em TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int versaoAntiga, int versaoNova) {
        // Exemplo didático: em um app real, migre os dados em vez de apagar.
        db.execSQL("DROP TABLE IF EXISTS " + TABELA);
        onCreate(db);
    }

    /** Grava todos os pontos em uma única transação. Retorna quantos foram gravados. */
    public int inserirTodos(List<PontoMapa> pontos) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            int total = 0;
            for (PontoMapa p : pontos) {
                ContentValues valores = new ContentValues();
                valores.put("nome", p.nome);
                valores.put("latitude", p.latitude);
                valores.put("longitude", p.longitude);
                db.insertOrThrow(TABELA, null, valores);   // viola o CHECK -> SQLiteConstraintException
                total++;
            }
            db.setTransactionSuccessful();
            return total;
        } finally {
            db.endTransaction();
        }
    }

    /** Lista os pontos gravados, do mais recente para o mais antigo. */
    public List<PontoMapa> listar() {
        List<PontoMapa> lista = new ArrayList<>();
        Cursor c = getReadableDatabase().query(TABELA,
                new String[]{"id", "nome", "latitude", "longitude"},
                null, null, null, null, "id DESC");
        try {
            while (c.moveToNext()) {
                PontoMapa p = new PontoMapa(c.getString(1), c.getDouble(2), c.getDouble(3));
                p.id = c.getLong(0);
                lista.add(p);
            }
        } finally {
            c.close();
        }
        return lista;
    }

    /** Apaga todos os pontos. Retorna quantas linhas foram removidas. */
    public int apagarTodos() {
        return getWritableDatabase().delete(TABELA, null, null);
    }
}
