package cn.hollis.llm.llmentor.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OrderManageService {
    // 根据订单号查询订单
    public String getOrderById(String orderId) {
        return "订单号：" + orderId;
    }

    // 退款
    public String refund(String orderId, String reason) {
        System.out.println("退款成功");
        return UUID.randomUUID().toString();
    }
}
