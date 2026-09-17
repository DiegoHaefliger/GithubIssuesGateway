package com.trade.triage.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.trade.triage.registry.model.ProjectEntry;
import com.trade.triage.registry.source.RegistrySource;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CachedProjectRegistryTest {

    private static final String JSON_VALIDO = """
            {
              "versao": "v1",
              "projetos": [
                {
                  "projeto": "trade-backend",
                  "diretorio": "/opt/projetos/trade",
                  "servicos": ["trade-backend"],
                  "pacotes_raiz": ["com.acme.trade"],
                  "repositorio": "acme/trade",
                  "branch_base": "master",
                  "comando_de_teste": ["mvn", "-B", "test"],
                  "ambientes": ["producao"],
                  "board": "acme/trade",
                  "allowlist": ["src/main/java/**/parser/**"],
                  "blast_radius": {"src/main/java/**/parser/**": "BAIXO"},
                  "limites": {"diff_max_linhas": 50, "diff_max_arquivos": 3,
                              "cards_por_hora": 10, "jobs_por_hora": 10},
                  "ativo": true
                }
              ]
            }
            """;

    private MutableSource source;
    private CachedProjectRegistry registry;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        RegistryValidator validator = new RegistryValidator(
                Validation.buildDefaultValidatorFactory().getValidator());
        source = new MutableSource(JSON_VALIDO);
        registry = new CachedProjectRegistry(source, objectMapper, validator);
    }

    @Test
    void naoTemProjetoAntesDeCarregar() {
        assertThat(registry.version()).isEqualTo("vazio");
        assertThat(registry.snapshot().projetos()).isEmpty();
    }

    @Test
    void carregaProjetoDoJson() {
        registry.reload();

        Optional<ProjectEntry> projeto = registry.findByServiceAndEnv("trade-backend", "producao");

        assertThat(registry.version()).isEqualTo("v1");
        assertThat(projeto).isPresent();
        assertThat(projeto.get().diretorio()).isEqualTo("/opt/projetos/trade");
        assertThat(projeto.get().repositorio()).isEqualTo("acme/trade");
    }

    @Test
    void naoEncontraServicoEmAmbienteNaoMonitorado() {
        registry.reload();

        assertThat(registry.findByServiceAndEnv("trade-backend", "homologacao")).isEmpty();
    }

    @Test
    void naoEncontraServicoDesconhecido() {
        registry.reload();

        assertThat(registry.findByServiceAndEnv("servico-orfao", "producao")).isEmpty();
    }

    @Test
    void mantemUltimaVersaoValidaQuandoJsonNovoEhInvalido() {
        registry.reload();
        source.content = "{\"versao\": \"\", \"projetos\": []}";

        registry.reload();

        assertThat(registry.version()).isEqualTo("v1");
        assertThat(registry.findByServiceAndEnv("trade-backend", "producao")).isPresent();
    }

    @Test
    void mantemUltimaVersaoValidaQuandoOrigemFalha() {
        registry.reload();
        source.falhar = true;

        registry.reload();

        assertThat(registry.version()).isEqualTo("v1");
    }

    @Test
    void rejeitaServicoMapeadoEmDoisProjetos() {
        registry.reload();
        source.content = duplicado();

        registry.reload();

        assertThat(registry.version()).isEqualTo("v1");
    }

    @Test
    void ignoraProjetoInativo() {
        source.content = JSON_VALIDO.replace("\"ativo\": true", "\"ativo\": false");

        registry.reload();

        assertThat(registry.findByServiceAndEnv("trade-backend", "producao")).isEmpty();
        assertThat(registry.findByRepository("acme/trade")).isPresent();
    }

    private String duplicado() {
        String projeto = JSON_VALIDO.substring(JSON_VALIDO.indexOf('{', JSON_VALIDO.indexOf("projetos")),
                JSON_VALIDO.lastIndexOf(']'));
        return """
                {"versao": "v2", "projetos": [%s, %s]}
                """.formatted(projeto, projeto);
    }

    private static final class MutableSource implements RegistrySource {

        private String content;
        private boolean falhar;

        private MutableSource(String content) {
            this.content = content;
        }

        @Override
        public String fetchRawJson() {
            if (falhar) {
                throw new RegistryLoadException("origem indisponivel");
            }
            return content;
        }

        @Override
        public String describe() {
            return "memoria";
        }
    }
}
