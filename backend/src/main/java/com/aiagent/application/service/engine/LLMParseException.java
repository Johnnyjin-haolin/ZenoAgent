package com.aiagent.application.service.engine;

import com.aiagent.domain.enums.ParseErrCode;
import lombok.Getter;

/**
 * @author johnny
 */
@Getter
public class LLMParseException extends RuntimeException {
    private final Integer errCode;
    private final String errMsg;
    private final String details;

    public LLMParseException(ParseErrCode parseErrCode) {
        this(parseErrCode, null);
    }

    public LLMParseException(ParseErrCode parseErrCode, String details) {
        this.errCode = parseErrCode.getCode();
        this.errMsg = parseErrCode.getDescription();
        this.details = details;
    }

    public LLMParseException(Integer errCode, String errMsg) {
        this(errCode, errMsg, null);
    }

    public LLMParseException(Integer errCode, String errMsg, String details) {
        this.errCode = errCode;
        this.errMsg = errMsg;
        this.details = details;
    }

    @Override
    public String getMessage() {
        if (details == null || details.isEmpty()) {
            return "ParseError(code=" + errCode + ", desc=" + errMsg + ")";
        }
        return "ParseError(code=" + errCode + ", desc=" + errMsg + ", " + details + ")";
    }
}
