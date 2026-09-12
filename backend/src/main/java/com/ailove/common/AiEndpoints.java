package com.ailove.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * AI 消耗端点判定：一次调用会产生 DashScope 费用的请求
 * （对话/智能体含 SSE、语音合成、追问建议）。限流与免费额度共用这一口径。
 */
public final class AiEndpoints {

    private AiEndpoints() {
    }

    public static boolean isAiConsumer(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        return path.startsWith("/ai/")
                || (path.equals("/api/tts") && "POST".equalsIgnoreCase(method))
                || (path.endsWith("/suggestions") && "POST".equalsIgnoreCase(method));
    }
}
