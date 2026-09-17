package com.trade.triage.board.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IssueDetailResponse(
        @JsonProperty("number") int number,
        @JsonProperty("title") String title,
        @JsonProperty("body") String body,
        @JsonProperty("state") String state) {
}
