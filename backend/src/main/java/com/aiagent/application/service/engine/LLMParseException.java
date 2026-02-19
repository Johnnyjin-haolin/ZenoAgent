package com.aiagent.application.service.engine;

import lombok.AllArgsConstructor;

/**
 * @author johnny
 */
@AllArgsConstructor
public class LLMParseException extends RuntimeException {
    Integer errCode;
    String errMsg;
}
