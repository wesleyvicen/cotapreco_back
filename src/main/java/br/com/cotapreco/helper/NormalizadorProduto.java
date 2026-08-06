package br.com.cotapreco.helper;

import java.text.Normalizer;
import java.util.Locale;

public final class NormalizadorProduto {
    private NormalizadorProduto() {}

    public static String normalizarEan(String valor) {
        if (valor == null || valor.isBlank()) return null;
        String numeros = valor.replaceAll("\\D", "");
        return numeros.isBlank() ? null : numeros;
    }

    public static String normalizarNome(String valor) {
        if (valor == null) return "";
        return Normalizer.normalize(valor.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    public static String identificadorCatalogo(String ean, String nome) {
        return ean == null ? "nome:" + normalizarNome(nome) : "ean:" + ean;
    }
}
