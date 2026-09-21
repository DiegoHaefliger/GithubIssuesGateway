package com.trade.triage.gate;

import com.trade.triage.persistence.entity.Decision;
import com.trade.triage.registry.model.BlastRadius;
import com.trade.triage.registry.model.ProjectEntry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

@Component
public class DecisionTablePolicyGate implements PolicyGate {

    private static final String SEVERIDADE_CRITICA = "critical";

    private final List<Clausula> clausulas = List.of(
            new Clausula("1", Decision.HUMAN, "incidente ativo declarado",
                    (fatos, projeto) -> fatos.incidenteAtivo(),
                    (fatos, projeto) -> "Ha uma janela de incidente ativa; enquanto ela durar, nenhuma correcao"
                            + " automatica avanca."),
            new Clausula("2", Decision.HUMAN, "ja houve tentativa automatica para este fingerprint",
                    (fatos, projeto) -> fatos.autoAttempts() >= 1,
                    (fatos, projeto) -> "Este fingerprint ja teve " + fatos.autoAttempts()
                            + " tentativa(s) automatica(s); a segunda vai sempre para humano."),
            // Sem arquivo no diff nao ha o que medir: o blast radius cai no default CRITICO e a
            // clausula 3 reprovava toda triagem que concluiu "nao ha o que corrigir", em loop.
            // Numero fora da sequencia de proposito: renumerar mudaria o sentido dos registros ja
            // gravados em decision_record e da tag de metrica.
            new Clausula("2b", Decision.HUMAN, "a triagem nao propos correcao",
                    (fatos, projeto) -> fatos.semProposta(),
                    (fatos, projeto) -> "O agente nao propos diff: nao ha patch para avaliar nem para"
                            + " aplicar. O card vai para humano decidir se fecha (bug ja corrigido ou"
                            + " nao reproduz) ou se pede nova triagem com mais evidencia."),
            new Clausula("3", Decision.HUMAN, "diff toca area de blast radius CRITICO",
                    (fatos, projeto) -> fatos.blastRadius() == BlastRadius.CRITICO,
                    (fatos, projeto) -> "Arquivos do diff: " + arquivos(fatos)
                            + ". Algum deles cai em area CRITICO do registro (ou fora de qualquer area mapeada)."),
            new Clausula("4", Decision.HUMAN, "diff toca arquivo fora da allowlist",
                    (fatos, projeto) -> !fatos.todosNaAllowlist(),
                    (fatos, projeto) -> "Arquivos do diff: " + arquivos(fatos)
                            + ". Pelo menos um nao casa com a allowlist do projeto " + projeto.projeto() + "."),
            new Clausula("5", Decision.HUMAN, "diff toca migracao, seguranca, IaC ou dependencia",
                    (fatos, projeto) -> fatos.tocaAreaProibida(),
                    (fatos, projeto) -> "Arquivos do diff: " + arquivos(fatos) + "."),
            new Clausula("6", Decision.HUMAN, "nao existe teste que reproduza o erro",
                    (fatos, projeto) -> !fatos.testeReproduzOErro(),
                    (fatos, projeto) -> "O teste novo nao ficou vermelho sem a correcao: ou a suite passou no"
                            + " codigo atual, ou o teste nao pode ser aplicado (ver log do GateFactsVerifier)."),
            new Clausula("7", Decision.HUMAN, "suite completa nao passa",
                    (fatos, projeto) -> !fatos.suiteCompletaPassa(),
                    (fatos, projeto) -> "O teste reproduz o erro, mas a suite completa falha com a correcao"
                            + " aplicada."),
            new Clausula("8", Decision.PROPOSE_PATCH, "diff acima do limite do projeto",
                    this::diffAcimaDoLimite,
                    (fatos, projeto) -> "Diff com " + fatos.linhasDoDiff() + " linha(s) em "
                            + fatos.arquivosDoDiff() + " arquivo(s); limite do projeto: "
                            + projeto.limites().diffMaxLinhas() + " linha(s) em "
                            + projeto.limites().diffMaxArquivos() + " arquivo(s)."),
            new Clausula("9", Decision.PROPOSE_PATCH, "severidade critica",
                    (fatos, projeto) -> SEVERIDADE_CRITICA.equalsIgnoreCase(fatos.severidade()),
                    (fatos, projeto) -> "O alerta chegou com severity=" + fatos.severidade()
                            + " (label da regra no Grafana). Com severidade critica o gate so abre PR para"
                            + " revisao humana, nunca aplica sozinho; para virar AUTO_FIX a regra precisa ter"
                            + " severidade abaixo de critical."),
            new Clausula("10", Decision.AUTO_FIX, "allowlist, teste vermelho para verde e diff pequeno",
                    (fatos, projeto) -> true,
                    (fatos, projeto) -> "Todas as clausulas de bloqueio passaram."));

    private final GateProperties properties;

    public DecisionTablePolicyGate(GateProperties properties) {
        this.properties = properties;
    }

    @Override
    public GateDecision decidir(GateFacts fatos, ProjectEntry projeto) {
        Clausula decisora = clausulas.stream()
                .filter(clausula -> clausula.casa(fatos, projeto))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Tabela de decisao sem clausula final"));
        return construir(decisora, fatos, projeto);
    }

    private GateDecision construir(Clausula clausula, GateFacts fatos, ProjectEntry projeto) {
        String detalhe = clausula.fato().apply(fatos, projeto) + "\n\n" + resumo(clausula, fatos, projeto);
        if (clausula.resultado() == Decision.AUTO_FIX && !properties.autoFixHabilitado()) {
            return new GateDecision(Decision.PROPOSE_PATCH, "clausula " + clausula.numero(),
                    clausula.explicacao() + ", mas AUTO_FIX esta desligado na postura de adocao", detalhe);
        }
        return new GateDecision(clausula.resultado(), "clausula " + clausula.numero(), clausula.explicacao(), detalhe);
    }

    private String resumo(Clausula decisora, GateFacts fatos, ProjectEntry projeto) {
        List<Clausula> anteriores = clausulas.subList(0, clausulas.indexOf(decisora));
        String passou = anteriores.isEmpty() ? "Nenhuma clausula anterior avaliada."
                : "Passou nas clausulas " + anteriores.stream().map(Clausula::numero)
                        .collect(java.util.stream.Collectors.joining(", ")) + ".";
        return passou + " Fatos avaliados: sem proposta=" + sim(fatos.semProposta())
                + ", incidente ativo=" + sim(fatos.incidenteAtivo())
                + ", tentativas automaticas=" + fatos.autoAttempts()
                + ", blast radius=" + fatos.blastRadius()
                + ", tudo na allowlist=" + sim(fatos.todosNaAllowlist())
                + ", area proibida=" + sim(fatos.tocaAreaProibida())
                + ", teste reproduz=" + sim(fatos.testeReproduzOErro())
                + ", suite passa=" + sim(fatos.suiteCompletaPassa())
                + ", diff=" + fatos.linhasDoDiff() + "/" + projeto.limites().diffMaxLinhas() + " linhas e "
                + fatos.arquivosDoDiff() + "/" + projeto.limites().diffMaxArquivos() + " arquivos"
                + ", severidade=" + fatos.severidade() + ".";
    }

    private static String sim(boolean valor) {
        return valor ? "sim" : "nao";
    }

    private static String arquivos(GateFacts fatos) {
        return fatos.arquivosTocados().isEmpty() ? "nenhum" : String.join(", ", fatos.arquivosTocados());
    }

    private boolean diffAcimaDoLimite(GateFacts fatos, ProjectEntry projeto) {
        return fatos.linhasDoDiff() > projeto.limites().diffMaxLinhas()
                || fatos.arquivosDoDiff() > projeto.limites().diffMaxArquivos();
    }

    private record Clausula(String numero, Decision resultado, String explicacao,
                            BiPredicate<GateFacts, ProjectEntry> condicao,
                            BiFunction<GateFacts, ProjectEntry, String> fato) {

        boolean casa(GateFacts fatos, ProjectEntry projeto) {
            return condicao.test(fatos, projeto);
        }
    }
}
