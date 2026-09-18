package com.trade.triage.orchestrator.web;

import com.trade.triage.board.github.WebhookSignatureVerifier;
import com.trade.triage.orchestrator.job.JobEnqueueResult;
import com.trade.triage.orchestrator.job.TriageJobService;
import com.trade.triage.orchestrator.outcome.OutcomeService;
import com.trade.triage.shared.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BoardWebhookController.class)
class BoardWebhookControllerTest {

    private static final String LABELED = """
            {
              "action": "labeled",
              "label": {"name": "auto-triage"},
              "issue": {"number": 123},
              "repository": {"full_name": "acme/trade"}
            }
            """;

    private static final String COMENTARIO = """
            {
              "action": "created",
              "issue": {"number": 123},
              "repository": {"full_name": "acme/trade"},
              "sender": {"type": "User"}
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WebhookSignatureVerifier verifier;

    @MockitoBean
    private TriageJobService jobService;

    @MockitoBean
    private OutcomeService outcomeService;

    @MockitoBean
    private WebhookDeduplicator deduplicator;

    @org.junit.jupiter.api.BeforeEach
    void aceitarEntregaNova() {
        when(deduplicator.primeiraEntrega(any(), anyString())).thenReturn(true);
    }

    @Test
    void entregaRepetidaNaoEnfileiraDeNovo() throws Exception {
        when(deduplicator.primeiraEntrega(any(), anyString())).thenReturn(false);

        mockMvc.perform(webhook("issues", LABELED))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.motivo").value("entrega repetida descartada"));

        verify(jobService, never()).enfileirar(anyString(), anyString());
    }

    @Test
    void labelAutoTriageEnfileiraJob() throws Exception {
        when(jobService.enfileirar("acme/trade#123", "issues.labeled"))
                .thenReturn(JobEnqueueResult.enfileirado("job-1"));

        mockMvc.perform(webhook("issues", LABELED))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.resultado").value("ENFILEIRADO"))
                .andExpect(jsonPath("$.job").value("job-1"));
    }

    @Test
    void outraLabelNaoEnfileira() throws Exception {
        mockMvc.perform(webhook("issues", LABELED.replace("auto-triage", "bug")))
                .andExpect(status().isAccepted());

        verify(jobService, never()).enfileirar(anyString(), anyString());
    }

    @Test
    void comentarioDeHumanoRetomaATriagem() throws Exception {
        when(jobService.enfileirar("acme/trade#123", "issue_comment.created"))
                .thenReturn(JobEnqueueResult.enfileirado("job-2"));

        mockMvc.perform(webhook("issue_comment", COMENTARIO))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.job").value("job-2"));
    }

    @Test
    void comentarioDoProprioBotNaoRetoma() throws Exception {
        mockMvc.perform(webhook("issue_comment", COMENTARIO.replace("\"User\"", "\"Bot\"")))
                .andExpect(status().isAccepted());

        verify(jobService, never()).enfileirar(anyString(), anyString());
    }

    @Test
    void comentarioDoProprioOrquestradorNaoRetoma() throws Exception {
        // TRIAGE_GITHUB_TOKEN e PAT pessoal: sender.type nunca vem "Bot" para a
        // analise que o proprio orquestrador posta. So o marcador distingue.
        String comentarioDoOrquestrador = """
                {
                  "action": "created",
                  "issue": {"number": 123},
                  "repository": {"full_name": "acme/trade"},
                  "sender": {"type": "User"},
                  "comment": {"body": "%s\\n## Analise do agente"}
                }
                """.formatted(com.trade.triage.board.BoardClient.MARCADOR_COMENTARIO_ORQUESTRADOR);

        mockMvc.perform(webhook("issue_comment", comentarioDoOrquestrador))
                .andExpect(status().isAccepted());

        verify(jobService, never()).enfileirar(anyString(), anyString());
    }

    @Test
    void pullRequestMergedRegistraDesfecho() throws Exception {
        mockMvc.perform(webhook("pull_request", """
                        {
                          "action": "closed",
                          "pull_request": {"number": 77, "merged": true},
                          "repository": {"full_name": "acme/trade"}
                        }
                        """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.resultado").value("EVENTO_REGISTRADO"));

        verify(outcomeService).registrarPullRequestFechado("acme/trade#77", true);
    }

    @Test
    void pullRequestFechadoSemMergeRegistraDesfecho() throws Exception {
        mockMvc.perform(webhook("pull_request", """
                        {
                          "action": "closed",
                          "pull_request": {"number": 77, "merged": false},
                          "repository": {"full_name": "acme/trade"}
                        }
                        """))
                .andExpect(status().isAccepted());

        verify(outcomeService).registrarPullRequestFechado("acme/trade#77", false);
    }

    @Test
    void cardFechadoRegistraResolucao() throws Exception {
        mockMvc.perform(webhook("issues", LABELED.replace("\"labeled\"", "\"closed\"")))
                .andExpect(status().isAccepted());

        verify(outcomeService).registrarCardFechado("acme/trade#123");
        verify(jobService, never()).enfileirar(anyString(), anyString());
    }

    @Test
    void pushEncaminhaMensagensDeCommitParaDeteccaoDeReversao() throws Exception {
        mockMvc.perform(webhook("push", """
                        {"commits": [{"message": "Revert \\"fix: x\\"\\n\\nFingerprint: f1"}]}
                        """))
                .andExpect(status().isAccepted());

        verify(outcomeService).registrarReversoes(List.of("Revert \"fix: x\"\n\nFingerprint: f1"));
    }

    @Test
    void assinaturaInvalidaEhRecusada() throws Exception {
        doThrow(new UnauthorizedException("Assinatura do webhook nao confere"))
                .when(verifier).verify(anyString(), any());

        mockMvc.perform(webhook("issues", LABELED))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(jobService, never()).enfileirar(anyString(), anyString());
    }

    @Test
    void payloadSemRepositorioEhRecusado() throws Exception {
        mockMvc.perform(webhook("issues", LABELED.replace("\"acme/trade\"", "\"\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAYLOAD"));
    }

    @Test
    void eventoDesconhecidoEhIgnorado() throws Exception {
        mockMvc.perform(webhook("star", "{}"))
                .andExpect(status().isAccepted());

        verify(jobService, never()).enfileirar(anyString(), anyString());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder webhook(
            String evento, String payload) {
        return post("/webhooks/board")
                .header("X-GitHub-Event", evento)
                .header("X-GitHub-Delivery", "entrega-1")
                .header("X-Hub-Signature-256", "sha256=qualquer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload);
    }
}
