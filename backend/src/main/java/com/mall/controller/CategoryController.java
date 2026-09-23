package com.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.auth.RequiresAdmin;
import com.mall.entity.Category;
import com.mall.service.CategoryService;
import com.mall.vo.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/category")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/list")
    public Result list() {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(Category::getParentId).orderByAsc(Category::getSort).orderByAsc(Category::getId);
        List<Category> categories = categoryService.list(wrapper);
        return Result.success(categories);
    }

    @RequiresAdmin
    @PostMapping
    public Result create(@RequestBody Category category) {
        categoryService.save(category);
        return Result.success();
    }

    @RequiresAdmin
    @PutMapping
    public Result update(@RequestBody Category category) {
        categoryService.updateById(category);
        return Result.success();
    }

    @RequiresAdmin
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id) {
        categoryService.removeById(id);
        return Result.success();
    }
}
