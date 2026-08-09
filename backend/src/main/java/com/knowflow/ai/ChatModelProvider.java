package com.knowflow.ai;

public interface ChatModelProvider {
    String answer(String systemPrompt, String userPrompt);
}
