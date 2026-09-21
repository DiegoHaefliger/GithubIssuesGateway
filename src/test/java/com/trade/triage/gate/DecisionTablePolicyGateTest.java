package com.trade.triage.gate;

import com.trade.triage.persistence.entity.Decision;
import com.trade.triage.registry.model.BlastRadius;
import com.trade.triage.registry.model.ProjectEntry;
import com.trade.triage.registry.model.ProjectLimits;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionTablePolicyGateTest {

    private final ProjectEntry projeto = new ProjectEntry(
            "trade", "/tmp/trade", List.of("trade-backend"), List.of("com.trade"), "acme/trade", "master", List.of("mvn", "test"),
            List.of("producao"), "acme/trade", List.of("src/main/java/**/parser/**"), Map.of(),
            new ProjectLimits(50, 3, 10, 10), true);

    private final PolicyGate gateComAutoFix = new DecisionTablePolicyGate(new GateProperties(true));
    private final PolicyGate gateSemAutoFix = new DecisionTablePolicyGate(new GateProperties(false));

    @Test
    void incidenteAtivoCongelaTudoNaClausula1() {
        GateDecision decisao = gateComAutoFix.decidir(elegivel().comIncidente(true).build(), projeto);

        assertThat(decisao.decisao()).isEqualTo(Decision.HUMAN);
        assertThat(decisao.regraDecisora()).isEqualTo("clausula 1");
    }

    @Test
    void segundaTentativaAutomaticaVaiParaHumano() {
        GateDecision decisao = gateComAutoFix.decidir(elegivel().comAutoAttempts(1).build(), projeto);

        assertThat(decisao.decisao()).isEqualTo(Decision.HUMAN);
        assertThat(decisao.regraDecisora()).isEqualTo("clausula 2");
    }

    @Test
    void triagemSemDiffParaNaClausulaPropriaEmVezDeBlastRadius() {
        GateDecision decisao = gateComAutoFix.decidir(
                elegivel().comArquivos(List.of()).comBlastRadius(BlastRadius.CRITICO).build(), projeto);

        assertThat(decisao.decisao()).isEqualTo(Decision.HUMAN);
        assertThat(decisao.regraDecisora()).isEqualTo("clausula 2b");
        assertThat(decisao.detalhe()).contains("nao propos diff").doesNotContain("blast radius CRITICO");
    }

    @Test
    void areaCriticaSempreVaiParaHumano() {
        GateDecision decisao = gateComAutoFix.decidir(
                elegivel().comBlastRadius(BlastRadius.CRITICO).build(), projeto);

        assertThat(decisao.decisao()).isEqualTo(Decision.HUMAN);
        assertThat(decisao.regraDecisora()).isEqualTo("clausula 3");
    }

    @Test
    void arquivoForaDaAllowlistVaiParaHumano() {
        GateDecision decisao = gateComAutoFix.decidir(elegivel().comAllowlist(false).build(), projeto);

        assertThat(decisao.regraDecisora()).isEqualTo("clausula 4");
        assertThat(decisao.decisao()).isEqualTo(Decision.HUMAN);
    }

    @Test
    void migracaoOuSegurancaVaiParaHumano() {
        GateDecision decisao = gateComAutoFix.decidir(elegivel().comAreaProibida(true).build(), projeto);

        assertThat(decisao.regraDecisora()).isEqualTo("clausula 5");
    }

    @Test
    void semTesteQueReproduzVaiParaHumano() {
        GateDecision decisao = gateComAutoFix.decidir(elegivel().comTesteReproduz(false).build(), projeto);

        assertThat(decisao.decisao()).isEqualTo(Decision.HUMAN);
        assertThat(decisao.regraDecisora()).isEqualTo("clausula 6");
    }

    @Test
    void suiteVermelhaVaiParaHumano() {
        GateDecision decisao = gateComAutoFix.decidir(elegivel().comSuitePassa(false).build(), projeto);

        assertThat(decisao.regraDecisora()).isEqualTo("clausula 7");
    }

    @Test
    void diffGrandeViraProposta() {
        GateDecision decisao = gateComAutoFix.decidir(elegivel().comLinhas(51).build(), projeto);

        assertThat(decisao.decisao()).isEqualTo(Decision.PROPOSE_PATCH);
        assertThat(decisao.regraDecisora()).isEqualTo("clausula 8");
    }

    @Test
    void muitosArquivosViraProposta() {
        GateDecision decisao = gateComAutoFix.decidir(
                elegivel().comArquivos(List.of("a", "b", "c", "d")).build(), projeto);

        assertThat(decisao.regraDecisora()).isEqualTo("clausula 8");
    }

    @Test
    void severidadeCriticaViraProposta() {
        GateDecision decisao = gateComAutoFix.decidir(elegivel().comSeveridade("critical").build(), projeto);

        assertThat(decisao.decisao()).isEqualTo(Decision.PROPOSE_PATCH);
        assertThat(decisao.regraDecisora()).isEqualTo("clausula 9");
        assertThat(decisao.detalhe())
                .contains("severity=critical")
                .contains("Passou nas clausulas 1, 2, 2b, 3, 4, 5, 6, 7, 8")
                .contains("teste reproduz=sim");
    }

    @Test
    void diffGrandeDetalhaTamanhoELimite() {
        GateDecision decisao = gateComAutoFix.decidir(elegivel().comLinhas(51).build(), projeto);

        assertThat(decisao.detalhe()).contains("51 linha(s)").contains("limite do projeto: 50");
    }

    @Test
    void primeiraClausulaNaoDizQuePassouEmNenhumaAnterior() {
        GateDecision decisao = gateComAutoFix.decidir(elegivel().comIncidente(true).build(), projeto);

        assertThat(decisao.detalhe()).contains("Nenhuma clausula anterior avaliada");
    }

    @Test
    void casoElegivelViraAutoFixQuandoHabilitado() {
        GateDecision decisao = gateComAutoFix.decidir(elegivel().build(), projeto);

        assertThat(decisao.decisao()).isEqualTo(Decision.AUTO_FIX);
        assertThat(decisao.regraDecisora()).isEqualTo("clausula 10");
    }

    @Test
    void casoElegivelViraPropostaEnquantoAutoFixEstaDesligado() {
        GateDecision decisao = gateSemAutoFix.decidir(elegivel().build(), projeto);

        assertThat(decisao.decisao()).isEqualTo(Decision.PROPOSE_PATCH);
        assertThat(decisao.regraDecisora()).isEqualTo("clausula 10");
        assertThat(decisao.explicacao()).contains("AUTO_FIX esta desligado");
    }

    @Test
    void primeiraClausulaQueCasaDecide() {
        GateFacts tudoRuim = elegivel()
                .comIncidente(true)
                .comAutoAttempts(3)
                .comBlastRadius(BlastRadius.CRITICO)
                .comTesteReproduz(false)
                .build();

        assertThat(gateComAutoFix.decidir(tudoRuim, projeto).regraDecisora()).isEqualTo("clausula 1");
    }

    private FatosBuilder elegivel() {
        return new FatosBuilder();
    }

    private static final class FatosBuilder {

        private List<String> arquivos = List.of("src/main/java/com/trade/parser/Candle.java");
        private int linhas = 10;
        private boolean testeReproduz = true;
        private boolean suitePassa = true;
        private boolean allowlist = true;
        private boolean areaProibida;
        private BlastRadius blastRadius = BlastRadius.BAIXO;
        private String severidade = "warning";
        private boolean incidente;
        private int autoAttempts;

        FatosBuilder comArquivos(List<String> arquivos) {
            this.arquivos = arquivos;
            return this;
        }

        FatosBuilder comLinhas(int linhas) {
            this.linhas = linhas;
            return this;
        }

        FatosBuilder comTesteReproduz(boolean valor) {
            this.testeReproduz = valor;
            return this;
        }

        FatosBuilder comSuitePassa(boolean valor) {
            this.suitePassa = valor;
            return this;
        }

        FatosBuilder comAllowlist(boolean valor) {
            this.allowlist = valor;
            return this;
        }

        FatosBuilder comAreaProibida(boolean valor) {
            this.areaProibida = valor;
            return this;
        }

        FatosBuilder comBlastRadius(BlastRadius valor) {
            this.blastRadius = valor;
            return this;
        }

        FatosBuilder comSeveridade(String valor) {
            this.severidade = valor;
            return this;
        }

        FatosBuilder comIncidente(boolean valor) {
            this.incidente = valor;
            return this;
        }

        FatosBuilder comAutoAttempts(int valor) {
            this.autoAttempts = valor;
            return this;
        }

        GateFacts build() {
            return new GateFacts(arquivos, linhas, testeReproduz, suitePassa, allowlist, areaProibida,
                    blastRadius, severidade, incidente, autoAttempts);
        }
    }
}
