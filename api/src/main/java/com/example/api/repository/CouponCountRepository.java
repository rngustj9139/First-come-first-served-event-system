package com.example.api.repository;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CouponCountRepository {

    private final RedisTemplate<String, String> redisTemplate;

    public CouponCountRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Long increment() { // redis의 incr 명령어를 사용하기 위한 메서드 정의
        return redisTemplate
                .opsForValue()
                .increment("coupon_count"); // coupon_count는 key를 의미
    }

}
