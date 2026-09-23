package com.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.auth.RequiresAdmin;
import com.mall.dto.ProductDTO;
import com.mall.entity.Category;
import com.mall.entity.Product;
import com.mall.entity.Sku;
import com.mall.exception.BusinessException;
import com.mall.exception.ErrorCode;
import com.mall.mapper.CategoryMapper;
import com.mall.mapper.SkuMapper;
import com.mall.service.ProductService;
import com.mall.vo.Result;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/product")
public class ProductController {

    private final ProductService productService;
    private final SkuMapper skuMapper;
    private final CategoryMapper categoryMapper;

    public ProductController(ProductService productService,
                             SkuMapper skuMapper,
                             CategoryMapper categoryMapper) {
        this.productService = productService;
        this.skuMapper = skuMapper;
        this.categoryMapper = categoryMapper;
    }

    @GetMapping("/list")
    public Result list(@RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "20") Integer size,
                       @RequestParam(required = false) Long categoryId,
                       @RequestParam(required = false) Integer status,
                       @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        if (categoryId != null) {
            wrapper.in(Product::getCategoryId, resolveCategoryIds(categoryId));
        }
        if (status != null) {
            wrapper.eq(Product::getStatus, status);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(Product::getName, keyword);
        }
        wrapper.orderByAsc(Product::getSort).orderByDesc(Product::getId);

        Page<Product> result = productService.page(new Page<>(page, size), wrapper);

        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getRecords());
        data.put("total", result.getTotal());
        return Result.success(data);
    }

    @GetMapping("/{id}")
    public Result detail(@PathVariable Long id) {
        Product product = productService.getById(id);
        if (product == null) {
            return Result.error(404, "商品不存在");
        }
        List<Sku> skus = skuMapper.selectList(
                new LambdaQueryWrapper<Sku>().eq(Sku::getProductId, id));
        product.setSkus(skus);
        return Result.success(product);
    }

    @RequiresAdmin
    @PostMapping
    public Result create(@RequestBody ProductDTO productDTO) {
        validateProduct(productDTO);
        Product product = new Product();
        applyProduct(product, productDTO);
        product.setSales(0);
        if (product.getStatus() == null) {
            product.setStatus(0);
        }
        productService.save(product);
        return Result.success(product);
    }

    @RequiresAdmin
    @PutMapping("/{id}")
    public Result update(@PathVariable Long id, @RequestBody ProductDTO productDTO) {
        Product product = requireProduct(id);
        validateProduct(productDTO);
        applyProduct(product, productDTO);
        productService.updateById(product);
        if (productDTO.getSalePrice() == null) {
            productService.lambdaUpdate()
                    .eq(Product::getId, id)
                    .set(Product::getSalePrice, null)
                    .update();
        }
        return Result.success(productService.getById(id));
    }

    @RequiresAdmin
    @PutMapping("/{id}/status")
    public Result updateStatus(@PathVariable Long id,
                               @RequestParam(required = false) Integer status) {
        Product product = requireProduct(id);
        int targetStatus = status == null
                ? (product.getStatus() != null && product.getStatus() == 1 ? 0 : 1)
                : status;
        if (targetStatus != 0 && targetStatus != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "商品状态只能为上架或下架");
        }
        product.setStatus(targetStatus);
        productService.updateById(product);
        return Result.success(targetStatus);
    }

    @RequiresAdmin
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id) {
        productService.removeById(id);
        return Result.success();
    }

    private List<Long> resolveCategoryIds(Long categoryId) {
        List<Long> categoryIds = new ArrayList<>();
        collectCategoryIds(categoryId, categoryIds);
        return categoryIds;
    }

    private void collectCategoryIds(Long categoryId, List<Long> categoryIds) {
        if (categoryIds.contains(categoryId)) {
            return;
        }
        categoryIds.add(categoryId);
        List<Category> children = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().eq(Category::getParentId, categoryId));
        for (Category child : children) {
            collectCategoryIds(child.getId(), categoryIds);
        }
    }

    private Product requireProduct(Long id) {
        Product product = productService.getById(id);
        if (product == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "商品不存在");
        }
        return product;
    }

    private void validateProduct(ProductDTO productDTO) {
        if (productDTO.getCategoryId() == null
                || categoryMapper.selectById(productDTO.getCategoryId()) == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "请选择有效分类");
        }
        if (productDTO.getName() == null || productDTO.getName().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "商品名称不能为空");
        }
        if (productDTO.getPrice() == null || productDTO.getPrice().signum() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "商品原价必须大于0");
        }
        if (productDTO.getSalePrice() != null) {
            if (productDTO.getSalePrice().signum() <= 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "优惠价必须大于0");
            }
            if (productDTO.getSalePrice().compareTo(productDTO.getPrice()) >= 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "优惠价必须低于原价");
            }
        }
        if (productDTO.getStock() != null && productDTO.getStock() < 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "库存不能小于0");
        }
        if (productDTO.getStatus() != null
                && productDTO.getStatus() != 0
                && productDTO.getStatus() != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "商品状态只能为上架或下架");
        }
    }

    private void applyProduct(Product product, ProductDTO productDTO) {
        product.setCategoryId(productDTO.getCategoryId());
        product.setName(productDTO.getName().trim());
        product.setSubtitle(productDTO.getSubtitle());
        product.setDescription(productDTO.getDescription());
        product.setImages(productDTO.getImages() == null || productDTO.getImages().isBlank()
                ? "[]"
                : productDTO.getImages().trim());
        product.setPrice(productDTO.getPrice());
        product.setSalePrice(productDTO.getSalePrice());
        product.setStock(productDTO.getStock() == null ? 0 : productDTO.getStock());
        product.setSort(productDTO.getSort() == null ? 0 : productDTO.getSort());
        if (productDTO.getStatus() != null) {
            product.setStatus(productDTO.getStatus());
        }
    }
}
