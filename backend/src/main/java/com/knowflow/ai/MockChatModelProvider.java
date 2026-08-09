package com.knowflow.ai;

public class MockChatModelProvider implements ChatModelProvider {
    @Override
    public String answer(String systemPrompt, String userPrompt) {
        return "这是 Mock AI 返回的示例答案。系统已经完成权限过滤、向量检索和上下文组装。\n\n"
                + "当前问题：" + userPrompt + "\n\n"
                + "配置 AI_PROVIDER=openai-compatible 并填写 AI_BASE_URL、AI_API_KEY 后，可切换为真实模型。";
    }
}
