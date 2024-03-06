package com.example.api.service;

import com.example.api.repository.CouponRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
     * 또한 redis는 싱글 스레드를 이용하기 때문에 동시성 문제가 발생하지 않는 장점을 가지고 있기 때문에 redis를 이용한다. 또한 redis는 인메모리 DB이기 때문에
     * DB Lock 보다 성능이 뛰어나다는 장점을 가지고 있다. (redis는 O(1)의 시간복잡도를 갖는다.)
     *
     * 하지만 mysql이 1분에 100개의 insert가 가능하다고 가정하고, 10시 정각에 10000개의 쿠폰생성 요청이 들어오는 경우 100분이 걸리게 된다. 따라서 10시 정각
     * 이후에 들어온 쿠폰 생성이 아닌 다른 요청들은 100분 이후에 처리 되게 된다(AWS와 nGrinder를 통해 부하테스트가 가능하다)
     */

    /**
     * 위의 문제를 해결하기 위해 kafka의 도입이 필요하다. (implementation 'org.springframework.kafka:spring-kafka' 의존성 추가 필요)
     * docker-compose.yml 파일 작성 후 인텔리제이 터미널에서 docker-compose up -d 명령어 실행
     * kafka란 분산 이벤트 스트리밍 플랫폼이다. (Producer, Topic, Consumer로 이루어저있으며 Topic은 큐와 같은 개념이다.) (이벤트 스트리밍이란 소스에서 목적지까지 이벤트를 실시간으로 스트리밍 하는 작업을 의미)
     * (토픽 생성) docker exec -it kafka kafka-topics.sh --bootstrap-server localhost:9092 --create --topic testTopic
     * (프로듀서 실행) docker exec -it kafka kafka-console-producer.sh --topic testTopic --broker-list 0.0.0.0:9092
     * (cmd 하나 더 실행 후 컨슈머 실행) docker exec -it kafka kafka-console-consumer.sh --topic testTopic --bootstrap-server localhost:9092
     * 이후 프로듀서가 실행된 cmd에 hello를 입력하면 컨슈머가 실행된 cmd에 hello라는 문자열이 보이게 된다.
     *
     * (쿠폰 로직 토픽 생성) docker exec -it kafka kafka-topics.sh --bootstrap-server localhost:9092 --create --topic coupon_create
     * (쿠폰 로직 컨슈머 생성) docker exec -it kafka kafka-console-consumer.sh --topic coupon_create --bootstrap-server localhost:9092 --key-deserializer "org.apache.kafka.common.serialization.StringDeserializer" --value-deserializer "org.apache.kafka.common.serialization.LongDeserializer"
     *
     * CouponCreatedConsumer 클래스 개발 후 ConsumerApplication 실행하고 아래 동시에여러명이응모_kafka() 테스트 수행하기
     */

    @Autowired
    private ApplyService applyService;

    @Autowired
    private CouponRepository couponRepository;

    @Test
    public void 한번만응모() { // success
        applyService.apply1(1L);

        long count = couponRepository.count();

        Assertions.assertThat(count).isEqualTo(1);
    }

    @Test
    public void 동시에여러명이응모() throws InterruptedException { // fail (첫 방법으로는 race condition 발생, redis를 이용할 경우 success)
        int threadCount = 1000;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            long userId = i;
            executorService.submit(() -> {
                try {
                    applyService.apply1(userId);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        long count = couponRepository.count();
        Assertions.assertThat(count).isEqualTo(100); // ApplyService에서 DB에 저장될 수 있는 쿠폰의 최대 개수를 100개로 설정해놓았음
    }

    /**
     * 실행 전 redis를 cmd에서 접속하고 flushall 명령어 수행 필요
     */
    @Test
    public void 동시에여러명이응모_kafka() throws InterruptedException { // Producer가 Consumer에게 데이터를 전송하고 Consumer가 데이터를 수신하고 처리가 끝나기 전에 Test가 종료되어서 fail이 발생한다. => Thread.sleep을 통해 10초의 간격을 발생시키면 된다.
        int threadCount = 1000;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            long userId = i;
            executorService.submit(() -> {
                try {
                    applyService.apply2(userId);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        Thread.sleep(10000);

        long count = couponRepository.count();
        Assertions.assertThat(count).isEqualTo(100); // ApplyService에서 DB에 저장될 수 있는 쿠폰의 최대 개수를 100개로 설정해놓았음
    }

}