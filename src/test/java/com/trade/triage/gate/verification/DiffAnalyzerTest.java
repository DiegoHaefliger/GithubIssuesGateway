package com.trade.triage.gate.verification;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiffAnalyzerTest {

    private static final String DIFF = """
            diff --git a/src/main/java/com/trade/parser/Candle.java b/src/main/java/com/trade/parser/Candle.java
            index 1111111..2222222 100644
            --- a/src/main/java/com/trade/parser/Candle.java
            +++ b/src/main/java/com/trade/parser/Candle.java
            @@ -10,6 +10,7 @@
                 public String exitReason() {
            -        return exitReason;
            +        return exitReason == null ? "" : exitReason;
                 }
            diff --git a/src/test/java/com/trade/parser/CandleTest.java b/src/test/java/com/trade/parser/CandleTest.java
            --- a/src/test/java/com/trade/parser/CandleTest.java
            +++ b/src/test/java/com/trade/parser/CandleTest.java
            @@ -1,0 +1,3 @@
            +    @Test
            +    void exitReasonNuncaEhNulo() {
            +    }
            """;

    private final DiffAnalyzer analyzer = new DiffAnalyzer();

    @Test
    void separaUmDiffPorArquivo() {
        List<FileDiff> arquivos = analyzer.analisar(DIFF);

        assertThat(arquivos).extracting(FileDiff::caminho).containsExactly(
                "src/main/java/com/trade/parser/Candle.java",
                "src/test/java/com/trade/parser/CandleTest.java");
    }

    @Test
    void contaLinhasAdicionadasERemovidasSemContarCabecalho() {
        List<FileDiff> arquivos = analyzer.analisar(DIFF);

        assertThat(arquivos.getFirst().linhasAdicionadas()).isEqualTo(1);
        assertThat(arquivos.getFirst().linhasRemovidas()).isEqualTo(1);
        assertThat(arquivos.getFirst().linhasAlteradas()).isEqualTo(2);
        assertThat(arquivos.get(1).linhasAdicionadas()).isEqualTo(3);
    }

    @Test
    void guardaOTextoDeCadaArquivoParaAplicacaoSeparada() {
        List<FileDiff> arquivos = analyzer.analisar(DIFF);

        assertThat(arquivos.getFirst().texto()).startsWith("diff --git a/src/main/java")
                .doesNotContain("CandleTest.java");
    }

    @Test
    void diffVazioNaoProduzArquivo() {
        assertThat(analyzer.analisar("   ")).isEmpty();
        assertThat(analyzer.analisar(null)).isEmpty();
    }
}
