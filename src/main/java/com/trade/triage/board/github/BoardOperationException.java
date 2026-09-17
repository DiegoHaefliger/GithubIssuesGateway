package com.trade.triage.board.github;

import com.trade.triage.shared.exception.BusinessException;

public class BoardOperationException extends BusinessException {

    public BoardOperationException(String message) {
        super(message);
    }

    public BoardOperationException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String code() {
        return "BOARD_OPERATION_FAILED";
    }
}
