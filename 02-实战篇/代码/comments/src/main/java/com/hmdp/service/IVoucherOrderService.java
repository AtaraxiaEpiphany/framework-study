package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    public Result secKillVoucherWithOptimize(Long voucherId);

    Result secKillVoucher(Long voucherId);

    @Transactional
    Result createVoucherOrder(Long voucherId, SeckillVoucher voucher, Long userId);
}
