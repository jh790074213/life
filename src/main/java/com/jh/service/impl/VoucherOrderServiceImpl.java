package com.jh.service.impl;

import com.jh.dto.Result;
import com.jh.entity.SeckillVoucher;
import com.jh.entity.VoucherOrder;
import com.jh.mapper.VoucherOrderMapper;
import com.jh.service.ISeckillVoucherService;
import com.jh.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jh.utils.RedisIdWorker;
import com.jh.utils.SimpleRedisLock;
import com.jh.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    public static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    private BlockingQueue<VoucherOrder> orderTask = new ArrayBlockingQueue<>(1024 * 1024);
    public static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            try {
                // 从阻塞队列中获取订单信息
                VoucherOrder voucherOrder = orderTask.take();
                handleVoucherOrder(voucherOrder);
            } catch (Exception e) {
                log.error("处理订单异常",e);
            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        // 锁用户，对于用户下的线程只有一个能抢，使用redisson实现，可以不使用锁
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("一人只能抢一单");
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }
    // 子线程拿不到代理对象，因此需要提前获取
    private IVoucherOrderService proxy;
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 获取用户的id
        Long userId = UserHolder.getUser().getId();
        // 1.执行Lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        // 2.判断结果是否为0
        if(result != 0){
            return Result.fail(result == 1 ? "库存不足" : "用户不能重复下单");
        }

        // 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        // 订单id
        voucherOrder.setId(orderId);
        // 用户id 从LocalThread获得
        voucherOrder.setUserId(userId);
        // 代金券id
        voucherOrder.setVoucherId(voucherId);
        // 保存到阻塞队列
        orderTask.add(voucherOrder);
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(orderId);

    }
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 5.一人一单
        Long userId = voucherOrder.getUserId();

        // 5.1查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        // 5.2判断是否存在
        if (count > 0) {
            log.error("用户已经购买过一次");
        }

        // 6.扣减库存,乐观锁解决超卖：where id = ? and stock > 0
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0) // where id = ? and stock > 0
                .update();
        if (!success) {
            log.error("库存不足");
        }
        // 创建订单
        save(voucherOrder);
    }
    // 不使用redis进行优惠券秒杀
    // @Override
    // public Result seckillVoucher(Long voucherId) {
    //     // 1.查询优惠卷
    //     SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
    //     // 2.判断秒杀是否开始
    //     if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
    //         // 未开始
    //         return Result.fail("秒杀未开始");
    //     }
    //     // 3.判断秒杀是否结束
    //     if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
    //         // 未开始
    //         return Result.fail("秒杀已经结束");
    //     }
    //     // 4.判断库存是否充足
    //     if (voucher.getStock() < 1) {
    //         return Result.fail("库存不足");
    //     }
    //     Long userId = UserHolder.getUser().getId();
    //     // 单机实现悲观锁
    //     // synchronized (userId.toString().intern()) {
    //     //     // 拿到代理对象，通过代理对象事务才生效
    //     //     IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
    //     //     return proxy.createVoucherOrder(voucherId);
    //     // }
    //     // 锁用户，对于用户下的线程只有一个能抢
    //     SimpleRedisLock redisLock = new SimpleRedisLock("order:"+userId,stringRedisTemplate);
    //     boolean isLock = redisLock.tryLock(1200);
    //     if (!isLock) {
    //         return Result.fail("一人只能抢一单");
    //     }
    //     try {
    //         // 拿到代理对象，通过代理对象事务才生效
    //         IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
    //         return proxy.createVoucherOrder(voucherId);
    //     } finally {
    //         redisLock.unlock();
    //     }
    // }

    /**
     * 创建订单，对用户使用悲观锁:synchronized
     *
     * @param voucherId 优惠券id
     * @return 订单id
     */
    // @Transactional
    // public Result createVoucherOrder(Long voucherId) {
    //     // 5.一人一单
    //     Long userId = UserHolder.getUser().getId();
    //
    //     // 5.1查询订单
    //     int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
    //     // 5.2判断是否存在
    //     if (count > 0) {
    //         return Result.fail("用户已经购买过一次");
    //     }
    //
    //     // 6.扣减库存,乐观锁解决超卖：where id = ? and stock > 0
    //     boolean success = seckillVoucherService.update()
    //             .setSql("stock = stock - 1")
    //             .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
    //             .update();
    //     if (!success) {
    //         return Result.fail("库存不足");
    //     }
    //     // 7.创建订单
    //     VoucherOrder voucherOrder = new VoucherOrder();
    //     long orderId = redisIdWorker.nextId("order");
    //     // 订单id
    //     voucherOrder.setId(orderId);
    //     // 用户id 从LocalThread获得
    //     voucherOrder.setUserId(userId);
    //     // 代金券id
    //     voucherOrder.setVoucherId(voucherId);
    //     save(voucherOrder);
    //     // 8.返回订单id
    //     return Result.ok(orderId);
    // }
}
