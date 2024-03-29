package com.example.api.service;

import com.example.api.domain.Coupon;
import com.example.api.producer.CouponCreateProducer;
import com.example.api.repository.AppliedUserRepository;
import com.example.api.repository.CouponCountRepository;
import com.example.api.repository.CouponRepository;
import org.springframework.stereotype.Service;

@Service
public class ApplyService {

    private final CouponRepository couponRepository;

    private final CouponCountRepository couponCountRepository;

    private final CouponCreateProducer couponCreateProducer;

    private final AppliedUserRepository appliedUserRepository;

    public ApplyService(CouponRepository couponRepository, CouponCountRepository couponCountRepository, CouponCreateProducer couponCreateProducer, AppliedUserRepository appliedUserRepository) {
        this.couponRepository = couponRepository;
        this.couponCountRepository = couponCountRepository;
        this.couponCreateProducer = couponCreateProducer;
        this.appliedUserRepository = appliedUserRepository;
    }

    public void apply1(Long userId) { // 쿠폰 발급 로직
//      long count = couponRepository.count();// 쿠폰발급 (쿠폰의 갯수 가져오기)
        Long count = couponCountRepository.increment(); // redis를 이용한 쿠폰 발급

        if (count > 100) { // 쿠폰의 갯수가 발급 가능한 갯수를 초과한 경우에는 발급하지 않음
            return;
        }

        // 쿠폰 발급이 가능한 경우
        couponRepository.save(new Coupon(userId));
    }

    public void apply2(Long userId) { // kafka를 이용한 쿠폰 발급 로직
        Long count = couponCountRepository.increment();

        if (count > 100) { // 쿠폰의 갯수가 발급 가능한 갯수를 초과한 경우에는 발급하지 않음
            return;
        }

        couponCreateProducer.create(userId);
    }

    public void apply3(Long userId) { // kafka를 이용한 쿠폰 발급 로직 + Redis의 SET 자료구조를 이용해 유저당 쿠폰을 한개씩만 발급할 수 있게 하는 로직
        Long add = appliedUserRepository.add(userId);

        if (add != 1) {
            return;
        }

        Long count = couponCountRepository.increment();

        if (count > 100) { // 쿠폰의 갯수가 발급 가능한 갯수를 초과한 경우에는 발급하지 않음
            return;
        }

        couponCreateProducer.create(userId);
    }

}
