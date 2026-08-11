package com.ecommerce.product.controller;
import com.ecommerce.common.Result;
import com.ecommerce.product.dto.*;
import com.ecommerce.product.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService categoryService;

    @PostMapping("/api/admin/categories")
    public Result<?> create(@RequestBody CreateCategoryReq req) {
        return Result.success(Map.of("categoryId", categoryService.createCategory(req)));
    }

    @PutMapping("/api/admin/categories/{categoryId}")
    public Result<?> update(@PathVariable Long categoryId, @RequestBody UpdateCategoryReq req) {
        categoryService.updateCategory(categoryId, req);
        return Result.success();
    }

    @DeleteMapping("/api/admin/categories/{categoryId}")
    public Result<?> delete(@PathVariable Long categoryId) {
        categoryService.deleteCategory(categoryId);
        return Result.success();
    }

    @GetMapping("/api/categories/tree")
    public Result<?> tree() {
        return Result.success(categoryService.getCategoryTree());
    }
}
