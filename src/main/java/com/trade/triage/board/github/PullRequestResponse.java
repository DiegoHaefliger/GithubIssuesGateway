package com.trade.triage.board.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PullRequestResponse(
        @JsonProperty("number") int number,
        @JsonProperty("html_url") String htmlUrl,
        @JsonProperty("node_id") String nodeId) {
}
