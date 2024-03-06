package com.example.api.consumer;

import com.example.api.domain.Coupon;
import com.example.api.domain.FailedEvent;
import com.example.api.repository.CouponRepository;
import com.example.api.repository.FailedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CouponCreatedConsumer {

    private final CouponRepository couponRepository;

    private final FailedEventRepository failedEventRepository;

    private final Logger logger = LoggerFactory.getLogger(CouponCreatedConsumer.class);

    public CouponCreatedConsumer(CouponRepository couponRepository, FailedEventRepository failedEventRepository) {
        this.couponRepository = couponRepository;
        this.failedEventRepository = failedEventRepository;
    }

    @KafkaListener(topics = "coupon_create", groupId = "group_1")
    public void listener(Long userID) {
        try {
            System.out.println("======= listener on =======" + userID.toString());
            couponRepository.save(new Coupon(userID));
        } catch (Exception e) {
            logger.error("error occurs during making coupon by" + userID.toString());
            failedEventRepository.save(new FailedEvent(userID)); // 쿠폰 생성에 실패할 경우 로그와 DB에 데이터를 남기고 이후 배치 프로그램을 통해 쿠폰을 생성해주면 된다.
        }

    }

}
