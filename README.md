# GithubIssuesBoardGateway

Triagem automatizada de erros de produção: alerta do Grafana vira card no GitHub
Issues com evidência coletada, o agente analisa em um runner isolado e um Policy
Gate determinístico decide o que vai para PR e o que vai para humano.

A arquitetura está em [`docs/ARQUITETURA_TRIAGEM_AUTOMATIZADA.md`](docs/ARQUITETURA_TRIAGEM_AUTOMATIZADA.md).
Este README descreve o que foi implementado e como rodar.

## Stack

Java 21, Spring Boot 3.5, Spring Data JPA (H2 em desenvolvimento, PostgreSQL em
produção), MapStruct com `unmappedTargetPolicy=ERROR`, JUnit 5 + Mockito + AssertJ.

## Registro de escopo

Nem o gateway nem o orquestrador conhecem projeto por código. Tudo que varia por
projeto — **caminho do diretório local**, repositório, branch base, serviços,
pacotes raiz, ambientes, comando de teste, allowlist, blast radius e limites —
vem de um arquivo JSON externo: [`config/projetos.json`](config/projetos.json).

```jsonc
{
  "versao": "exemplo-1",
  "projetos": [
    {
      "projeto": "trade-backend",
      "diretorio": "/opt/projetos/trade",
      "servicos": ["trade-backend"],
      "pacotes_raiz": ["com.trade"],
      "repositorio": "exemplo/trade",
      "branch_base": "master",
      "comando_de_teste": ["mvn", "-B", "test"],
      "ambientes": ["producao"],
      "board": "exemplo/trade",
      "allowlist": ["src/main/java/**/parser/**"],
      "blast_radius": { "src/main/java/**/execution/**": "CRITICO" },
      "limites": { "diff_max_linhas": 50, "diff_max_arquivos": 3,
                   "cards_por_hora": 10, "jobs_por_hora": 10 },
      "ativo": true
    }
  ]
}
```

O registro é recarregado periodicamente. Um JSON inválido é rejeitado inteiro e a
última versão válida continua valendo; nunca se degrada para "sem restrição".
Serviço que não casa com nenhuma entrada não gera card — vira métrica de log órfão.
Caminho não classificado no `blast_radius` é tratado como `CRITICO`.

Para carregar de um bucket S3 em vez do disco:

```bash
TRIAGE_REGISTRY_ORIGEM=HTTP TRIAGE_REGISTRY_URL=https://meu-bucket.s3.amazonaws.com/projetos.json
```

## Como rodar

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
mvn test          # suíte completa
mvn spring-boot:run
```

Variáveis principais:

| Variável | Para que serve |
|---|---|
| `TRIAGE_GITHUB_TOKEN` | escrita no board, disparo do workflow e push da branch |
| `TRIAGE_GITHUB_WEBHOOK_SECRET` | HMAC dos webhooks do board e do contexto/sinal do runner |
| `TRIAGE_GRAFANA_WEBHOOK_TOKEN` | credencial do webhook do Grafana; **sem ela nenhum alerta entra** |
| `TRIAGE_REGISTRY_ARQUIVO` / `TRIAGE_REGISTRY_URL` | origem do registro de escopo |
| `TRIAGE_LOKI_URL`, `TRIAGE_PROMETHEUS_URL` | coleta de evidência |
| `TRIAGE_URL_PUBLICA` | URL que o runner usa para sinalizar conclusão |
| `TRIAGE_AUTO_FIX` | liga AUTO_FIX; nasce em `false` (§11 da arquitetura) |
| `TRIAGE_KILL_SWITCH` | desliga toda ação automática |
| `TRIAGE_POLLING` | liga o polling de fallback quando o webhook do board não é confiável |

## Endpoints

| Rota | Origem | O que faz |
|---|---|---|
| `POST /webhooks/grafana` | Grafana | ingere alertas, deduplica e abre o card |
| `GET /jobs/{jobId}/contexto` | GitHub Actions | entrega card e evidência ao runner, que não tem credencial |
| `POST /webhooks/board` | GitHub | `issues.labeled` e `issue_comment.created` enfileiram job; `issues.closed`, `pull_request.closed` e `push` registram desfecho |
| `POST /webhooks/runner/{jobId}` | GitHub Actions | apenas sinaliza o fim; o resultado é puxado depois |
| `GET /actuator/metrics` | operação | métricas do §9 |

Nenhum handler HTTP dispara runner nem roda teste: grava estado e responde `202`.

Todos autenticam. O board e o runner assinam com HMAC-SHA256
(`X-Hub-Signature-256`); o Grafana manda `Authorization: Bearer` ou `Basic`,
configurado no contact point. Falha fechada: token não configurado significa
webhook recusado, nunca aberto.

## Desfecho e métricas

O ciclo só fecha quando o desfecho volta. `pull_request.closed` marca o registro
de decisão como `MERGED` ou `REJEITADO`; um commit de revert que carregue o
trailer `Fingerprint:` marca `REVERTIDO` e devolve o fingerprint para humano;
`issues.closed` resolve o fingerprint, e um card fechado sem nenhuma decisão do
gate conta como falso positivo da regra que disparou.

É isso que alimenta o §9: aceite por decisão, reversão, falso positivo por
`rule_id`, tempo até a primeira análise, MTTR por severidade e minutos de Actions
(lidos do endpoint de timing da execução). O consumo de quota da assinatura não é
observável daqui — o que se mede é execução de runner por projeto.

## Parar tudo

O kill switch não depende do sistema que ele desliga: basta criar o arquivo.

```bash
touch config/PARAR_TRIAGEM     # para toda ação automática
touch config/INCIDENTE_ATIVO   # suprime cards durante incidente declarado
```

## Runner

O agente roda no repositório monitorado, não aqui. Copie
[`docs/runner/triage-runner.yml`](docs/runner/triage-runner.yml) para
`.github/workflows/` do projeto monitorado e configure os secrets
`CLAUDE_CODE_OAUTH_TOKEN` e `TRIAGE_WEBHOOK_SECRET`.

O runner não tem credencial do board nem permissão de push. Ele busca o contexto
em `GET /jobs/{jobId}/contexto` — card inteiro, comentários (inclusive a resposta
humana que retomou a triagem) e o pacote de evidência, tudo já limpo e marcado
como dado não-confiável —, escreve `resultado.json` como artefato e encerra. Quem
lê, verifica e publica é o orquestrador.

## O que o gate verifica sozinho

Nenhum fato vem da palavra do agente. Antes de decidir, o gate clona o diretório
do projeto em um worktree descartável, aplica **só o diff do teste** e exige a
suíte vermelha; depois aplica o diff da correção e exige a suíte verde. Também
mede o diff, checa a allowlist e classifica o blast radius.

## Estado da implementação

| Parte | Branch | Situação |
|---|---|---|
| Projeto base (Java 21, Spring Boot) | `feat/01-base-project` | pronto |
| Registro de escopo em JSON | `feat/02-project-registry` | pronto |
| Fingerprint, normalização e scrub | `feat/03-fingerprint-scrub` | pronto |
| Estado persistido | `feat/04-persistence` | pronto |
| Cliente do board (GitHub Issues) | `feat/05-github-board` | pronto |
| Ingestão do alerta até o card | `feat/06-gateway-ingest` | pronto |
| Policy Gate | `feat/07-policy-gate` | pronto |
| Ciclo de vida do job | `feat/08-job-lifecycle` | pronto |
| Resultado e verificação executável | `feat/09-result-verification` | pronto |
| Publicação da decisão | `feat/10-decision-publication` | pronto |
| Métricas e documentação | `feat/11-metrics-docs` | pronto |
| URI do pacote de evidência no PR | `feat/12-evidence-uri` | pronto |
| Contexto do card e evidência para o runner | `feat/13-runner-context` | pronto |
| Autenticação do webhook do Grafana | `feat/14-grafana-webhook-auth` | pronto |
| Desfecho do PR e resolução do fingerprint | `feat/15-pr-outcome` | pronto |
| Card agregado de tempestade | `feat/16-storm-card` | pronto |
| Auto-merge no AUTO_FIX | `feat/17-auto-merge` | pronto |
| Watchdog cancela o runner órfão | `feat/18-watchdog-cancel` | pronto |
| Scrub campo a campo de log estruturado | `feat/19-structured-scrub` | pronto |
| Idempotência por id de entrega | `feat/20-webhook-idempotency` | pronto |
| Deploys e painel na evidência | `feat/21-evidence-gaps` | pronto |
| Métricas do §9 completas | `feat/22-metrics-complete` | pronto |
| Polling de fallback | `feat/23-polling-fallback` | pronto |

Fora do escopo desta implementação, por decisão da arquitetura: as regras de
alerta do Grafana (artefato versionado à parte) e o dono do kill switch (§12.2).

Duas lacunas permanecem por falta de fonte, e o pacote de evidência as declara em
vez de fingir que coletou: trace distribuído (Tempo não existe no ambiente) e
estado de flags e config vigente (não há serviço de config).
