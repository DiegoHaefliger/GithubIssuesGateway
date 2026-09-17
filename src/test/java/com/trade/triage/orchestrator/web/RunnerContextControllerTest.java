package com.trade.triage.orchestrator.web;

import com.trade.triage.board.github.WebhookSignatureVerifier;
import com.trade.triage.orchestrator.job.JobContextService;
import com.trade.triage.shared.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RunnerContextController.class)
class RunnerContextControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WebhookSignatureVerifier verifier;

    @MockitoBean
    private JobContextService contextService;

    @Test
    void entregaOContextoParaAssinaturaValida() throws Exception {
        when(contextService.contextoDe("job-1", null)).thenReturn(new JobContextResponse(
                "job-1", "acme/trade#123", "f1", "acme/trade", "master",
                JobContextResponse.AVISO_DE_CONTEUDO_HOSTIL, "titulo", "corpo", List.of(), null));

        mockMvc.perform(get("/jobs/{jobId}/contexto", "job-1")
                        .header("X-Hub-Signature-256", "sha256=qualquer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.card").value("acme/trade#123"))
                .andExpect(jsonPath("$.branch_base").value("master"))
                .andExpect(jsonPath("$.aviso").value(JobContextResponse.AVISO_DE_CONTEUDO_HOSTIL));
    }

    @Test
    void assinaturaInvalidaNaoEntregaContexto() throws Exception {
        doThrow(new UnauthorizedException("Assinatura do webhook nao confere"))
                .when(verifier).verify(anyString(), any());

        mockMvc.perform(get("/jobs/{jobId}/contexto", "job-1")
                        .header("X-Hub-Signature-256", "sha256=errada"))
                .andExpect(status().isUnauthorized());

        verify(contextService, never()).contextoDe(anyString(), any());
    }

    @Test
    void semAssinaturaNaoEntregaContexto() throws Exception {
        doThrow(new UnauthorizedException("Assinatura do webhook ausente ou malformada"))
                .when(verifier).verify(anyString(), any());

        mockMvc.perform(get("/jobs/{jobId}/contexto", "job-1"))
                .andExpect(status().isUnauthorized());
    }
}
