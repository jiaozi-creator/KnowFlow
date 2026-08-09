package com.knowflow.chat;

import com.knowflow.common.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class ChatRateLimitService {
    private final StringRedisTemplate redisTemplate;

    public ChatRateLimitService(StringRedisTemplate redisTemplate) { this.redisTemplate = redisTemplate; }

    public void check(Long tenantId, Long userId) {
        String key = "knowflow:chat:rate:" + tenantId + ":" + userId;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) redisTemplate.expire(key, Duration.ofMinutes(1));
            if (count != null && count > 20) throw BusinessException.badRequest("请求过于频繁，请稍后再试");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ignored) {
            // Redis unavailable must not take down the core RAG flow in local development.
        }
    }
}
