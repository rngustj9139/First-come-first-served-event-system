package com.example.api.service;

import com.example.api.domain.Coupon;
import com.example.api.repository.CouponCountRepository;
import com.example.api.repository.CouponRepository;
import org.springframework.stereotype.Service;

@Service
public class ApplyService {

    private final CouponRepository couponRepository;

    private final CouponCountRepository couponCountRepository;

    public ApplyService(CouponRepository couponRepository, CouponCountRepository couponCountRepository) {
        this.couponRepository = couponRepository;
        this.couponCountRepository = couponCountRepository;
    }

    public void apply(Long userId) { // 쿠폰 발급 로직
//      long count = couponRepository.count();// 쿠폰발급 (쿠폰의 갯수 가져오기)
        Long count = couponCountRepository.increment(); // redis를 이용한 쿠폰 발급

        if (count > 100) { // 쿠폰의 갯수가 발급 가능한 갯수를 초과한 경우에는 발급하지 않음
            return;
        }

        // 쿠폰 발급이 가능한 경우
        couponRepository.save(new Coupon(userId));
    }

}
