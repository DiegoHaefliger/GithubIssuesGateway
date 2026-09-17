package com.trade.triage.gate.verification;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DiffAnalyzer {

    private static final String INICIO_DE_ARQUIVO = "diff --git ";
    private static final String CAMINHO_DESTINO = "+++ b/";
    private static final String CAMINHO_NULO = "+++ /dev/null";

    public List<FileDiff> analisar(String diff) {
        if (diff == null || diff.isBlank()) {
            return List.of();
        }
        List<FileDiff> arquivos = new ArrayList<>();
        Acumulador atual = null;
        for (String linha : diff.split("\n", -1)) {
            if (linha.startsWith(INICIO_DE_ARQUIVO)) {
                adicionar(arquivos, atual);
                atual = new Acumulador(caminhoDoCabecalho(linha));
            }
            if (atual == null) {
                continue;
            }
            atual.consumir(linha);
        }
        adicionar(arquivos, atual);
        return List.copyOf(arquivos);
    }

    private void adicionar(List<FileDiff> arquivos, Acumulador acumulador) {
        if (acumulador != null) {
            arquivos.add(acumulador.fechar());
        }
    }

    private String caminhoDoCabecalho(String linha) {
        String[] partes = linha.substring(INICIO_DE_ARQUIVO.length()).split(" ");
        String destino = partes[partes.length - 1];
        return destino.startsWith("b/") ? destino.substring(2) : destino;
    }

    private static final class Acumulador {

        private final StringBuilder texto = new StringBuilder();
        private String caminho;
        private int adicionadas;
        private int removidas;

        private Acumulador(String caminho) {
            this.caminho = caminho;
        }

        private void consumir(String linha) {
            texto.append(linha).append('\n');
            if (linha.startsWith(CAMINHO_DESTINO)) {
                caminho = linha.substring(CAMINHO_DESTINO.length()).trim();
            } else if (linha.startsWith(CAMINHO_NULO)) {
                return;
            } else if (linha.startsWith("+") && !linha.startsWith("+++")) {
                adicionadas++;
            } else if (linha.startsWith("-") && !linha.startsWith("---")) {
                removidas++;
            }
        }

        private FileDiff fechar() {
            return new FileDiff(caminho, texto.toString(), adicionadas, removidas);
        }
    }
}
