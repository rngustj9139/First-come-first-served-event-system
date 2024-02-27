package com.example.api.service;

import com.example.api.repository.CouponRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ApplyServiceTest {

    /**
     * Spring Boot에서는 spring-data-redis 의존성을 추가하면 자동 구성(Auto-Configuration) 기능을 통해 Redis 설정을 기본값으로 채워준다.
     * 기본적으로 RedisConnectionFactory, RedisTemplate, StringRedisTemplate가 자동으로 구성되어 localhost:6379 주소의
     * Redis 서버에 연결하려고 시도하는데, 이 기본값을 사용하기 때문에 별도로 application.yml 또는 application.properties 파일에
     * Redis 설정을 하지 않아도 바로 Redis를 사용할 수 있다.
     *
     * docker exec -it (redis의 컨테이너 id) redis-cli
     * incr coupon_count
     */

    /**
     * DB Lock을 이용하면 서비스의 성능이 느려지기 때문에 Redis를 이용해야한다.
     * 이유는 아래와 같다.
     *    10:00 lock 획득
     *    10:01 쿠폰개수 획득
     *    10:02 쿠폰 생성
     *    10:03 lock 해제
     * 위의 플로우처럼 A 라는 유저가 10시에 lock 을 획득하였고 10:03 에 lock 을 해제할 때 다른유저들은 10:00 ~ 10:03 까지 쿠폰생성 메소드에 진입을 하지 못하고
     * lock 을 획득할 때까지 대기하게 된다. 그로인하여 처리량이 낮아지게 되고 이는 성능 저하를 초래함을 의미한다.
     */

    @Autowired
    private ApplyService applyService;

    @Autowired
    private CouponRepository couponRepository;

    @Test
    public void 한번만응모() { // success
        applyService.apply(1L);

        long count = couponRepository.count();

        Assertions.assertThat(count).isEqualTo(1);
    }

    @Test
    public void 동시에여러명이응모() throws InterruptedException { // fail (race condition 발생)
        int threadCount = 1000;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            long userId = i;
            executorService.submit(() -> {
                try {
                    applyService.apply(userId);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        long count = couponRepository.count();
        Assertions.assertThat(count).isEqualTo(100); // ApplyService에서 DB에 저장될 수 있는 쿠폰의 최대 개수를 100개로 설정해놓았음
    }

}