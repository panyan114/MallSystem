package com.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.auth.AuthUser;
import com.mall.entity.Cart;
import com.mall.entity.Product;
import com.mall.entity.Sku;
import com.mall.mapper.SkuMapper;
import com.mall.service.CartService;
import com.mall.service.ProductService;
import com.mall.vo.Result;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;
    private final ProductService productService;
    private final SkuMapper skuMapper;

    public CartController(CartService cartService,
                          ProductService productService,
                          SkuMapper skuMapper) {
        this.cartService = cartService;
        this.productService = productService;
        this.skuMapper = skuMapper;
    }

    @PostMapping
    public Result add(@RequestBody Cart cart,
                      @RequestAttribute(AuthUser.REQUEST_ATTRIBUTE) AuthUser authUser) {
        if (cart.getProductId() == null) {
            return Result.error("商品ID不能为空");
        }
        int quantity = cart.getQuantity() == null ? 1 : cart.getQuantity();
        if (quantity <= 0) {
            return Result.error("数量必须大于0");
        }

        Long uid = authUser.id();
        Product product = productService.getById(cart.getProductId());
        if (product == null || product.getStatus() == null || product.getStatus() != 1) {
            return Result.error("商品不存在");
        }
        if (cart.getSkuId() == null && hasSku(product.getId())) {
            return Result.error("请选择商品规格");
        }
        Sku sku = resolveSku(cart.getProductId(), cart.getSkuId());
        if (cart.getSkuId() != null && sku == null) {
            return Result.error("商品规格不存在");
        }

        Cart existing = findCart(uid, cart.getProductId(), cart.getSkuId());
        int currentQuantity = existing == null || existing.getQuantity() == null ? 0 : existing.getQuantity();
        int targetQuantity = currentQuantity + quantity;
        if (targetQuantity > availableStock(product, sku)) {
            return Result.error("库存不足");
        }

        if (existing != null) {
            existing.setQuantity(targetQuantity);
            cartService.updateById(existing);
            return Result.success(existing.getId());
        }

        cart.setId(null);
        cart.setUserId(uid);
        cart.setQuantity(quantity);
        cartService.save(cart);
        return Result.success(cart.getId());
    }

    @GetMapping
    public Result list(@RequestAttribute(AuthUser.REQUEST_ATTRIBUTE) AuthUser authUser) {
        Long uid = authUser.id();
        List<Cart> carts = cartService.list(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, uid)
                .orderByDesc(Cart::getId));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Cart cart : carts) {
            Map<String, Object> item = toItem(cart);
            if (item != null) {
                result.add(item);
            }
        }
        return Result.success(result);
    }

    @PutMapping("/{id}")
    public Result updateQuantity(@PathVariable Long id,
                                 @RequestBody Cart form,
                                 @RequestAttribute(AuthUser.REQUEST_ATTRIBUTE) AuthUser authUser) {
        Long uid = authUser.id();
        Cart existing = getOwnedCart(id, uid);
        if (existing == null) {
            return Result.error(404, "购物车商品不存在");
        }

        int quantity = form.getQuantity() == null ? existing.getQuantity() : form.getQuantity();
        if (quantity <= 0) {
            return Result.error("数量必须大于0");
        }

        Product product = productService.getById(existing.getProductId());
        if (product == null) {
            return Result.error("商品不存在");
        }
        Sku sku = resolveSku(existing.getProductId(), existing.getSkuId());
        if (quantity > availableStock(product, sku)) {
            return Result.error("库存不足");
        }

        existing.setQuantity(quantity);
        cartService.updateById(existing);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id,
                         @RequestAttribute(AuthUser.REQUEST_ATTRIBUTE) AuthUser authUser) {
        boolean removed = cartService.remove(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getId, id)
                .eq(Cart::getUserId, authUser.id()));
        if (!removed) {
            return Result.error(404, "购物车商品不存在");
        }
        return Result.success();
    }

    @DeleteMapping
    public Result clear(@RequestAttribute(AuthUser.REQUEST_ATTRIBUTE) AuthUser authUser) {
        cartService.remove(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, authUser.id()));
        return Result.success();
    }

    private Cart findCart(Long userId, Long productId, Long skuId) {
        LambdaQueryWrapper<Cart> wrapper = new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getProductId, productId);
        if (skuId == null) {
            wrapper.isNull(Cart::getSkuId);
        } else {
            wrapper.eq(Cart::getSkuId, skuId);
        }
        return cartService.getOne(wrapper);
    }

    private Cart getOwnedCart(Long id, Long userId) {
        return cartService.getOne(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getId, id)
                .eq(Cart::getUserId, userId));
    }

    private Sku resolveSku(Long productId, Long skuId) {
        if (skuId == null) {
            return null;
        }
        Sku sku = skuMapper.selectById(skuId);
        if (sku == null || !productId.equals(sku.getProductId())) {
            return null;
        }
        return sku;
    }

    private boolean hasSku(Long productId) {
        Long count = skuMapper.selectCount(new LambdaQueryWrapper<Sku>()
                .eq(Sku::getProductId, productId));
        return count != null && count > 0;
    }

    private int availableStock(Product product, Sku sku) {
        if (sku != null && sku.getStock() != null) {
            return sku.getStock();
        }
        return product.getStock() == null ? 0 : product.getStock();
    }

    private Map<String, Object> toItem(Cart cart) {
        Product product = productService.getById(cart.getProductId());
        if (product == null) {
            return null;
        }
        Sku sku = resolveSku(cart.getProductId(), cart.getSkuId());
        BigDecimal price = sku != null && sku.getPrice() != null
                ? sku.getPrice()
                : (product.getSalePrice() != null ? product.getSalePrice() : product.getPrice());
        int quantity = cart.getQuantity() == null ? 1 : cart.getQuantity();

        Map<String, Object> item = new HashMap<>();
        item.put("id", cart.getId());
        item.put("productId", product.getId());
        item.put("skuId", cart.getSkuId());
        item.put("productName", product.getName());
        item.put("name", product.getName());
        item.put("skuDesc", sku != null ? sku.getSpecDesc() : "默认规格");
        item.put("price", price);
        item.put("quantity", quantity);
        item.put("stock", availableStock(product, sku));
        item.put("image", sku != null && sku.getImage() != null && !sku.getImage().isEmpty()
                ? sku.getImage()
                : firstImage(product.getImages()));
        item.put("subtotal", price.multiply(BigDecimal.valueOf(quantity)));
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
