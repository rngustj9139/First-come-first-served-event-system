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
     *
     * 아래 test를 수행하기 전에
     * flushall 명령어 수행 필요
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
     *
     * 또한 redis는 싱글 스레드를 이용하기 때문에 동시성 문제가 발생하지 않는 장점을 가지고 있기 때문에 redis를 이용한다.
     *
     * 하지만 mysql이 1분에 100개의 insert가 가능하다고 가정하고, 10시 정각에 10000개의 쿠폰생성 요청이 들어오는 경우 100분이 걸리게 된다. 따라서 10시 정각
     * 이후에 들어온 쿠폰 생성이 아닌 다른 요청들은 100분 이후에 처리 되게 된다(AWS와 nGrinder를 통해 부하테스트가 가능하다)
     */

    /**
     * 위의 문제를 해결하기 위해 kafka의 도입이 필요하다.
     * docker-compose.yml 파일 작성 후 인텔리제이 터미널에서 docker-compose up -d 명령어 실행
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