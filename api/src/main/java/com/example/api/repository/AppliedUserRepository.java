package com.example.api.repository;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AppliedUserRepository { // 유저당 쿠폰을 한개씩만 발급할 수 있도록 하는 레포지토리(Redis의 SET 자료구조 이용 - SADD testUser 1)

    private final RedisTemplate<String, String> redisTemplate;

    public AppliedUserRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Long add(Long userId) {
        return redisTemplate
                .opsForSet() // Redis의 SET 자료구조 이용
                .add("applied_user", userId.toString()); // SADD key value
    }

}
