package com.ecommerce.product.service;
import com.ecommerce.product.dto.*;
import java.util.List;

public interface CategoryService {
    Long createCategory(CreateCategoryReq req);
    void updateCategory(Long categoryId, UpdateCategoryReq req);
    void deleteCategory(Long categoryId);
    List<CategoryTreeResp> getCategoryTree();
}
