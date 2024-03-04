package com.example.consumer.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CouponCreatedConsumer {

    @KafkaListener(topics = "coupon_create", groupId = "group_1")
    public void listener(Long userID) {
        System.out.println(userID);
    }

}
