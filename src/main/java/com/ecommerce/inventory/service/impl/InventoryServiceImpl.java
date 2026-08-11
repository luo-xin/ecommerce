package com.ecommerce.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.common.BusinessException;
import com.ecommerce.common.ErrorCode;
import com.ecommerce.common.dto.PageResult;
import com.ecommerce.inventory.dto.*;
import com.ecommerce.inventory.entity.Inventory;
import com.ecommerce.inventory.entity.InventoryLog;
import com.ecommerce.inventory.mapper.InventoryLogMapper;
import com.ecommerce.inventory.mapper.InventoryMapper;
import com.ecommerce.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private static final String REDIS_KEY_PREFIX = "inventory:available:";

    private final InventoryMapper inventoryMapper;
    private final InventoryLogMapper logMapper;
    private final StringRedisTemplate redisTemplate;

    // ApplicationReadyEvent: sync MySQL -> Redis
    @EventListener(ApplicationReadyEvent.class)
    public void initRedisCache() {
        try {
            List<Inventory> list = inventoryMapper.selectList(null);
            for (Inventory inv : list) {
                redisTemplate.opsForValue().set(
                        REDIS_KEY_PREFIX + inv.getProductId(),
                        String.valueOf(inv.getAvailableStock()));
            }
            log.info("Inventory Redis cache initialized: {} products", list.size());
        } catch (Exception e) {
            log.error("Failed to init inventory Redis cache, degraded to MySQL reads", e);
        }
    }

    // Internal: called by ProductService on product creation
    @Override
    @Transactional
    public void initForProduct(Long productId) {
        Inventory inv = new Inventory();
        inv.setProductId(productId);
        inv.setTotalStock(0);
        inv.setAvailableStock(0);
        inv.setFrozenStock(0);
        inv.setVersion(0);
        inventoryMapper.insert(inv);
        redisTemplate.opsForValue().set(REDIS_KEY_PREFIX + productId, "0");
    }

    // Admin: initialize stock (first time only)
    @Override
    @Transactional
    public Long initInventory(InitInventoryReq req) {
        if (req.getQuantity() == null || req.getQuantity() <= 0)
            throw new BusinessException(ErrorCode.INVALID_QUANTITY);
        Inventory existing = inventoryMapper.selectByProductId(req.getProductId());
        if (existing == null) throw new BusinessException(ErrorCode.INVENTORY_NOT_FOUND);
        if (existing.getAvailableStock() > 0 || existing.getTotalStock() > 0)
            throw new BusinessException(ErrorCode.INVENTORY_ALREADY_INITIALIZED);

        inventoryMapper.addStock(req.getProductId(), req.getQuantity());
        redisTemplate.opsForValue().set(REDIS_KEY_PREFIX + req.getProductId(),
                String.valueOf(req.getQuantity()));
        insertLog(req.getProductId(), "RESTOCK", req.getQuantity(), 0, req.getQuantity(), null, "初始化库存");
        return existing.getId();
    }

    // Admin: restock
    @Override
    @Transactional
    public RestockResp restock(Long productId, RestockReq req) {
        if (req.getQuantity() == null || req.getQuantity() <= 0)
            throw new BusinessException(ErrorCode.INVALID_QUANTITY);
        Inventory inv = inventoryMapper.selectByProductId(productId);
        if (inv == null) throw new BusinessException(ErrorCode.INVENTORY_NOT_FOUND);

        int beforeStock = inv.getAvailableStock();
        inventoryMapper.addStock(productId, req.getQuantity());
        redisTemplate.opsForValue().increment(REDIS_KEY_PREFIX + productId, req.getQuantity());

        insertLog(productId, "RESTOCK", req.getQuantity(), beforeStock,
                beforeStock + req.getQuantity(), null, req.getRemark() != null ? req.getRemark() : "补货入库");

        return RestockResp.builder()
                .productId(productId).beforeStock(beforeStock)
                .afterStock(beforeStock + req.getQuantity()).build();
    }

    // Public: query stock
    @Override
    public StockResp queryStock(Long productId) {
        String key = REDIS_KEY_PREFIX + productId;
        String cached = redisTemplate.opsForValue().get(key);
        int stock;
        if (cached != null) {
            stock = Integer.parseInt(cached);
        } else {
            Inventory inv = inventoryMapper.selectByProductId(productId);
            if (inv == null) throw new BusinessException(ErrorCode.INVENTORY_NOT_FOUND);
            stock = inv.getAvailableStock();
            redisTemplate.opsForValue().set(key, String.valueOf(stock));
        }
        return StockResp.builder().productId(productId).availableStock(stock).build();
    }

    // Admin: query logs
    @Override
    public PageResult<InventoryLogItem> getLogs(Long productId, int page, int size) {
        Page<InventoryLog> p = logMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<InventoryLog>()
                        .eq(InventoryLog::getProductId, productId)
                        .orderByDesc(InventoryLog::getCreatedAt));
        List<InventoryLogItem> items = p.getRecords().stream().map(l ->
                InventoryLogItem.builder()
                        .logId(l.getId()).productId(l.getProductId())
                        .changeType(l.getChangeType()).changeQuantity(l.getChangeQuantity())
                        .beforeStock(l.getBeforeStock()).afterStock(l.getAfterStock())
                        .orderNo(l.getOrderNo()).remark(l.getRemark())
                        .createdAt(l.getCreatedAt()).build()
        ).collect(Collectors.toList());
        return PageResult.<InventoryLogItem>builder()
                .total(p.getTotal()).page(page).size(size).items(items).build();
    }

    // Internal: atomic deduct (called inside OrderService @Transactional)
    // Redis DECRBY is NOT inside @Transactional
    @Override
    @Transactional
    public void deduct(Long productId, int qty, String orderNo) {
        String key = REDIS_KEY_PREFIX + productId;

        // Ensure Redis key exists (fallback if missing)
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            Inventory inv = inventoryMapper.selectByProductId(productId);
            if (inv != null) redisTemplate.opsForValue().set(key, String.valueOf(inv.getAvailableStock()));
        }

        Long newVal = redisTemplate.opsForValue().decrement(key, qty);
        if (newVal == null || newVal < 0) {
            // Rollback Redis immediately
            if (newVal != null) redisTemplate.opsForValue().increment(key, qty);
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }

        int beforeStock = (int)(newVal + qty);
        int affected = inventoryMapper.deductMysql(productId, qty);
        if (affected == 0) {
            // MySQL guard failed — Redis already decremented, roll it back
            redisTemplate.opsForValue().increment(key, qty);
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }
        insertLog(productId, "DEDUCT", -qty, beforeStock, (int)((long)newVal), orderNo, "订单扣减");
    }

    // Internal: rollback (REQUIRES_NEW -- independent transaction)
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rollback(Long productId, int qty, String orderNo) {
        Inventory inv = inventoryMapper.selectByProductId(productId);
        if (inv == null) throw new BusinessException(ErrorCode.INVENTORY_NOT_FOUND);
        int beforeStock = inv.getAvailableStock();

        inventoryMapper.rollbackMysql(productId, qty);
        redisTemplate.opsForValue().increment(REDIS_KEY_PREFIX + productId, qty);
        insertLog(productId, "ROLLBACK", qty, beforeStock, beforeStock + qty, orderNo, "库存回滚");
    }

    private void insertLog(Long productId, String type, int changeQty,
                           int before, int after, String orderNo, String remark) {
        InventoryLog log = new InventoryLog();
        log.setProductId(productId);
        log.setChangeType(type);
        log.setChangeQuantity(changeQty);
        log.setBeforeStock(before);
        log.setAfterStock(after);
        log.setOrderNo(orderNo);
        log.setRemark(remark);
        log.setCreatedAt(LocalDateTime.now());
        logMapper.insert(log);
    }
}
