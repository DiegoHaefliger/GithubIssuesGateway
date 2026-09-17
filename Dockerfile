# ─── Build ───────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package

# ─── Runtime ─────────────────────────────────────────────────────────────────
# JDK completo (nao JRE) + Maven + Node/npm + git: o proprio processo clona o
# projeto monitorado num worktree temporario e roda o `comando_de_teste` dele
# (GateFactsVerifier, GitPatchPublisher) — o runtime desta imagem e' tambem o
# runtime dos projetos que ela verifica.
FROM eclipse-temurin:21-jdk-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl git maven ca-certificates gnupg \
    && curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

# UID/GID fixos (nao --system generico): o deploy precisa saber o numero de
# antemao pra ajustar o dono do volume nomeado ANTES do primeiro start —
# volume novo monta como root:root por cima do chown da imagem, e o processo
# nao-root apanha "permission denied" escrevendo evidencia (ver deploy-server.sh).
RUN groupadd --system --gid 10001 triage && useradd --system --uid 10001 --gid triage --create-home triage

WORKDIR /app
COPY --from=build /build/target/triage-gateway-*.jar app.jar
# Registro de projetos versionado no repo (docs/ARQUITETURA_TRIAGEM_AUTOMATIZADA.md
# §3.10): cada imagem carrega o config vigente no commit que a gerou. Editar
# projetos.json e mergear redeploya com o registro novo — nao ha S3 hoje.
COPY config config

# var/evidencia (pacotes de evidencia) e var/worktrees (clones temporarios dos
# projetos monitorados) precisam sobreviver a um restart do container, os dois
# como volume montado pelo compose/service create.
RUN mkdir -p var/evidencia var/worktrees && chown -R triage:triage /app
USER triage

EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
