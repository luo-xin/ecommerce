package com.ecommerce.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecommerce.common.BusinessException;
import com.ecommerce.common.ErrorCode;
import com.ecommerce.product.dto.*;
import com.ecommerce.product.entity.Category;
import com.ecommerce.product.mapper.CategoryMapper;
import com.ecommerce.product.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;

    @Override
    public Long createCategory(CreateCategoryReq req) {
        Long parentId = req.getParentId() == null ? 0L : req.getParentId();

        if (!Long.valueOf(0L).equals(parentId)) {
            Category parent = categoryMapper.selectById(parentId);
            if (parent == null) throw new BusinessException(ErrorCode.PARENT_CATEGORY_NOT_FOUND);
            if (!Long.valueOf(0L).equals(parent.getParentId())) throw new BusinessException(ErrorCode.CATEGORY_LEVEL_EXCEEDED);
        }

        Long count = categoryMapper.selectCount(new LambdaQueryWrapper<Category>()
                .eq(Category::getName, req.getName()).eq(Category::getParentId, parentId));
        if (count > 0) throw new BusinessException(ErrorCode.CATEGORY_NAME_EXISTS);

        Category cat = new Category();
        cat.setName(req.getName());
        cat.setParentId(parentId);
        cat.setSort(req.getSort() != null ? req.getSort() : 0);
        cat.setStatus(1);
        categoryMapper.insert(cat);
        return cat.getId();
    }

    @Override
    public void updateCategory(Long categoryId, UpdateCategoryReq req) {
        Category cat = categoryMapper.selectById(categoryId);
        if (cat == null) throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        if (req.getName() != null) cat.setName(req.getName());
        if (req.getSort() != null) cat.setSort(req.getSort());
        if (req.getStatus() != null) cat.setStatus(req.getStatus());
        categoryMapper.updateById(cat);
    }

    @Override
    public void deleteCategory(Long categoryId) {
        Category cat = categoryMapper.selectById(categoryId);
        if (cat == null) throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);

        Long childCount = categoryMapper.selectCount(
                new LambdaQueryWrapper<Category>().eq(Category::getParentId, categoryId));
        if (childCount > 0) throw new BusinessException(ErrorCode.CATEGORY_HAS_CHILDREN);

        if (categoryMapper.countProductsByCategoryId(categoryId) > 0)
            throw new BusinessException(ErrorCode.CATEGORY_HAS_PRODUCTS);

        categoryMapper.deleteById(categoryId);
    }

    @Override
    public List<CategoryTreeResp> getCategoryTree() {
        List<Category> all = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().eq(Category::getStatus, 1)
                        .orderByAsc(Category::getSort));

        Map<Long, CategoryTreeResp> map = new LinkedHashMap<>();
        List<CategoryTreeResp> roots = new ArrayList<>();

        for (Category c : all) {
            CategoryTreeResp node = CategoryTreeResp.builder()
                    .categoryId(c.getId()).name(c.getName())
                    .sort(c.getSort()).status(c.getStatus())
                    .children(new ArrayList<>()).build();
            map.put(c.getId(), node);
        }
        for (Category c : all) {
            if (Long.valueOf(0L).equals(c.getParentId())) roots.add(map.get(c.getId()));
            else if (map.containsKey(c.getParentId()))
                map.get(c.getParentId()).getChildren().add(map.get(c.getId()));
        }
        return roots;
    }
}
