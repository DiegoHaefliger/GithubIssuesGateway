package com.trade.triage.board.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IssueCommentResponse(
        @JsonProperty("body") String body,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("user") User user) {

    public String autor() {
        return user == null ? "desconhecido" : user.login();
    }

    public String tipoDeAutor() {
        return user == null ? "desconhecido" : user.type();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(@JsonProperty("login") String login, @JsonProperty("type") String type) {
    }
}
