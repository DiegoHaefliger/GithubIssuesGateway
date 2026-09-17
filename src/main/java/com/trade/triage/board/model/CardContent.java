package com.trade.triage.board.model;

import java.util.List;

public record CardContent(String titulo, String corpo, List<String> labels) {

    public CardContent {
        labels = labels == null ? List.of() : List.copyOf(labels);
    }
}
