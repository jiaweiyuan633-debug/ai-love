package com.ailove.common;

/**
 * AI 上游（DashScope/通义千问）错误归类：区分“内容审核拦截”与“服务不可用”，
 * 供全局异常处理器与 SSE 错误帧复用，避免向用户暴露白盒报错。
 */
public final class AiErrorMessages {

    private static final String[] MODERATION_KEYWORDS = {"DataInspectionFailed", "sensitive", "敏感", "内容审核"};

    private AiErrorMessages() {
    }

    /** 沿异常 cause 链判断是否命中内容审核拦截（消息含平台不允许的内容）。 */
    public static boolean isModerationBlocked(Throwable e) {
        Throwable t = e;
        while (t != null) {
            String msg = String.valueOf(t.getMessage());
            for (String keyword : MODERATION_KEYWORDS) {
                if (msg.contains(keyword)) {
                    return true;
                }
            }
            t = t.getCause() == t ? null : t.getCause();
        }
        return false;
    }

    /** 用户可见的错误文案。 */
    public static String friendly(Throwable e) {
        if (isModerationBlocked(e)) {
            return "这条消息可能包含平台不允许的内容，换个说法再试试吧";
        }
        return "AI 服务暂时不可用，请稍后再试";
    }
}
