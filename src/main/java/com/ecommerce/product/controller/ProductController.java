package com.ecommerce.product.controller;
import com.ecommerce.common.Result;
import com.ecommerce.product.dto.*;
import com.ecommerce.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    @PostMapping("/api/admin/products")
    public Result<?> create(@RequestBody CreateProductReq req) {
        return Result.success(Map.of("productId", productService.createProduct(req)));
    }

    @PutMapping("/api/admin/products/{productId}")
    public Result<?> update(@PathVariable Long productId, @RequestBody UpdateProductReq req) {
        productService.updateProduct(productId, req);
        return Result.success();
    }

    @PutMapping("/api/admin/products/{productId}/on-sale")
    public Result<?> onSale(@PathVariable Long productId) {
        productService.onSale(productId);
        return Result.success();
    }

    @PutMapping("/api/admin/products/{productId}/off-sale")
    public Result<?> offSale(@PathVariable Long productId) {
        productService.offSale(productId);
        return Result.success();
    }

    @DeleteMapping("/api/admin/products/{productId}")
    public Result<?> delete(@PathVariable Long productId) {
        productService.deleteProduct(productId);
        return Result.success();
    }

    @GetMapping("/api/products")
    public Result<?> list(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(productService.listProducts(categoryId, keyword, page, Math.min(size, 100)));
    }

    @GetMapping("/api/products/{productId}")
    public Result<ProductDetailResp> detail(@PathVariable Long productId) {
        return Result.success(productService.getProductDetail(productId));
    }

    @PostMapping("/api/admin/products/{productId}/images")
    public Result<?> addImage(@PathVariable Long productId, @RequestBody AddImageReq req) {
        return Result.success(Map.of("imageId", productService.addImage(productId, req)));
    }

    @DeleteMapping("/api/admin/products/{productId}/images/{imageId}")
    public Result<?> deleteImage(@PathVariable Long productId, @PathVariable Long imageId) {
        productService.deleteImage(productId, imageId);
        return Result.success();
    }
}
