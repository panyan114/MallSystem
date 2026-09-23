package com.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.auth.AuthUser;
import com.mall.entity.Collect;
import com.mall.entity.Product;
import com.mall.service.CollectService;
import com.mall.service.ProductService;
import com.mall.vo.Result;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/collect")
public class CollectController {

    private final CollectService collectService;
    private final ProductService productService;

    public CollectController(CollectService collectService, ProductService productService) {
        this.collectService = collectService;
        this.productService = productService;
    }

    @GetMapping("/list")
    public Result list(@RequestAttribute(AuthUser.REQUEST_ATTRIBUTE) AuthUser authUser) {
        Long uid = authUser.id();
        List<Collect> collects = collectService.list(
                new LambdaQueryWrapper<Collect>()
                        .eq(Collect::getUserId, uid)
                        .orderByDesc(Collect::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Collect c : collects) {
            Map<String, Object> item = toItem(c);
            if (item != null) {
                result.add(item);
            }
        }
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result detail(@PathVariable Long id,
                         @RequestAttribute(AuthUser.REQUEST_ATTRIBUTE) AuthUser authUser) {
        Collect collect = getOwnedCollect(id, authUser.id());
        if (collect == null) {
            return Result.error(404, "收藏不存在");
        }
        Map<String, Object> item = toItem(collect);
        if (item == null) {
            return Result.error(404, "关联商品不存在");
        }
        return Result.success(item);
    }

    @PostMapping
    public Result add(@RequestBody Collect collect,
                      @RequestAttribute(AuthUser.REQUEST_ATTRIBUTE) AuthUser authUser) {
        if (collect.getProductId() == null) {
            return Result.error("商品ID不能为空");
        }
        Long uid = authUser.id();
        Product product = productService.getById(collect.getProductId());
        if (product == null) {
            return Result.error("商品不存在");
        }
        Long count = collectService.count(new LambdaQueryWrapper<Collect>()
                .eq(Collect::getUserId, uid)
                .eq(Collect::getProductId, collect.getProductId()));
        if (count != null && count > 0) {
            return Result.error("该商品已收藏");
        }
        collect.setId(null);
        collect.setUserId(uid);
        collectService.save(collect);
        return Result.success(collect.getId());
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable Long id,
                         @RequestBody Collect form,
                         @RequestAttribute(AuthUser.REQUEST_ATTRIBUTE) AuthUser authUser) {
        Long uid = authUser.id();
        Collect existing = getOwnedCollect(id, uid);
        if (existing == null) {
            return Result.error(404, "收藏不存在");
        }
        if (form.getProductId() != null && !form.getProductId().equals(existing.getProductId())) {
            Product product = productService.getById(form.getProductId());
            if (product == null) {
                return Result.error("商品不存在");
            }
            Long count = collectService.count(new LambdaQueryWrapper<Collect>()
                    .eq(Collect::getUserId, uid)
                    .eq(Collect::getProductId, form.getProductId())
                    .ne(Collect::getId, id));
            if (count != null && count > 0) {
                return Result.error("该商品已收藏");
            }
            existing.setProductId(form.getProductId());
        }
        collectService.updateById(existing);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result remove(@PathVariable Long id,
                         @RequestAttribute(AuthUser.REQUEST_ATTRIBUTE) AuthUser authUser) {
        boolean removed = collectService.remove(new LambdaQueryWrapper<Collect>()
                .eq(Collect::getId, id)
                .eq(Collect::getUserId, authUser.id()));
        if (!removed) {
            return Result.error(404, "收藏不存在");
        }
        return Result.success();
    }

    private Collect getOwnedCollect(Long id, Long userId) {
        return collectService.getOne(new LambdaQueryWrapper<Collect>()
                .eq(Collect::getId, id)
                .eq(Collect::getUserId, userId));
    }

    private Map<String, Object> toItem(Collect collect) {
        Product product = productService.getById(collect.getProductId());
        if (product == null) {
            return null;
        }
        Map<String, Object> item = new HashMap<>();
        item.put("id", collect.getId());
        item.put("productId", product.getId());
        item.put("name", product.getName());
        item.put("price", product.getSalePrice() != null ? product.getSalePrice() : product.getPrice());
        item.put("image", firstImage(product.getImages()));
        item.put("createTime", collect.getCreateTime());
        return item;
    }

    private String firstImage(String images) {
        if (images == null || images.isEmpty()) {
            return "";
        }
        String trimmed = images.trim();
        if (trimmed.startsWith("[")) {
            int start = trimmed.indexOf('"');
            int end = trimmed.indexOf('"', start + 1);
            if (start >= 0 && end > start) {
                return trimmed.substring(start + 1, end);
            }
        }
        return trimmed;
    }
}
