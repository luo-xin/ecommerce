package com.ecommerce.product.service;
import com.ecommerce.common.dto.PageResult;
import com.ecommerce.product.dto.*;

public interface ProductService {
    Long createProduct(CreateProductReq req);
    void updateProduct(Long productId, UpdateProductReq req);
    void onSale(Long productId);
    void offSale(Long productId);
    void deleteProduct(Long productId);
    PageResult<ProductListItem> listProducts(Long categoryId, String keyword, int page, int size);
    ProductDetailResp getProductDetail(Long productId);
    Long addImage(Long productId, AddImageReq req);
    void deleteImage(Long productId, Long imageId);
}
