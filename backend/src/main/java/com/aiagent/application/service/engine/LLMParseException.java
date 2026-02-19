package com.aiagent.application.service.engine;

import com.aiagent.domain.enums.ParseErrCode;
import lombok.AllArgsConstructor;

/**
 * @author johnny
 */
@AllArgsConstructor
public class LLMParseException extends RuntimeException {
    Integer errCode;
    String errMsg;
    LLMParseException(ParseErrCode parseErrCode) {
        this.errCode = parseErrCode.getCode();
        this.errMsg=parseErrCode.getDescription();
    }
}
