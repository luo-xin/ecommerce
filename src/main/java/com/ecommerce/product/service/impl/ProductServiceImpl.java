package com.ecommerce.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.common.BusinessException;
import com.ecommerce.common.ErrorCode;
import com.ecommerce.common.dto.PageResult;
import com.ecommerce.inventory.service.InventoryService;
import com.ecommerce.product.dto.*;
import com.ecommerce.product.entity.*;
import com.ecommerce.product.mapper.*;
import com.ecommerce.product.service.ProductService;
import com.ecommerce.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final ProductImageMapper imageMapper;
    private final InventoryService inventoryService;

    @Override
    @Transactional
    public Long createProduct(CreateProductReq req) {
        Category cat = categoryMapper.selectById(req.getCategoryId());
        if (cat == null) throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        if (Long.valueOf(0L).equals(cat.getParentId())) throw new BusinessException(ErrorCode.NOT_SECOND_LEVEL_CATEGORY);

        Product p = new Product();
        p.setCategoryId(req.getCategoryId());
        p.setName(req.getName());
        p.setDescription(req.getDescription());
        p.setPrice(req.getPrice());
        p.setStatus("OFF_SALE");
        productMapper.insert(p);

        // Side effect: initialize inventory record
        inventoryService.initForProduct(p.getId());
        return p.getId();
    }

    @Override
    @Transactional
    public void updateProduct(Long productId, UpdateProductReq req) {
        Product p = getExistingProduct(productId);
        if ("ON_SALE".equals(p.getStatus()) && req.getPrice() != null
                && req.getPrice().compareTo(p.getPrice()) != 0)
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_PRICE_ON_SALE);
        if (req.getCategoryId() != null) p.setCategoryId(req.getCategoryId());
        if (req.getName() != null) p.setName(req.getName());
        if (req.getDescription() != null) p.setDescription(req.getDescription());
        if (req.getPrice() != null) p.setPrice(req.getPrice());
        productMapper.updateById(p);
    }

    @Override
    @Transactional
    public void onSale(Long productId) {
        Product p = getExistingProduct(productId);
        if (!"OFF_SALE".equals(p.getStatus())) throw new BusinessException(ErrorCode.PRODUCT_STATUS_NOT_ALLOWED);
        p.setStatus("ON_SALE");
        productMapper.updateById(p);
    }

    @Override
    @Transactional
    public void offSale(Long productId) {
        Product p = getExistingProduct(productId);
        if (!"ON_SALE".equals(p.getStatus())) throw new BusinessException(ErrorCode.PRODUCT_STATUS_NOT_ALLOWED);
        p.setStatus("OFF_SALE");
        productMapper.updateById(p);
    }

    @Override
    @Transactional
    public void deleteProduct(Long productId) {
        Product p = getExistingProduct(productId);
        if ("ON_SALE".equals(p.getStatus())) throw new BusinessException(ErrorCode.MUST_OFF_SALE_FIRST);
        if (productMapper.countActiveOrders(productId) > 0)
            throw new BusinessException(ErrorCode.PRODUCT_HAS_ACTIVE_ORDER);
        p.setStatus("DELETED");
        productMapper.updateById(p);
    }

    @Override
    public PageResult<ProductListItem> listProducts(Long categoryId, String keyword, int page, int size) {
        LambdaQueryWrapper<Product> qw = new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, "ON_SALE");

        if (categoryId != null) {
            List<Long> childIds = categoryMapper.selectList(
                    new LambdaQueryWrapper<Category>().eq(Category::getParentId, categoryId))
                    .stream().map(Category::getId).collect(Collectors.toList());
            childIds.add(categoryId);
            qw.in(Product::getCategoryId, childIds);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.like(Product::getName, keyword);
        }

        Page<Product> p = productMapper.selectPage(new Page<>(page, size), qw);
        List<Long> productIds = p.getRecords().stream().map(Product::getId).collect(Collectors.toList());
        Map<Long, String> mainImages = productIds.isEmpty() ? new HashMap<>() :
                imageMapper.selectList(new LambdaQueryWrapper<ProductImage>()
                        .eq(ProductImage::getType, "MAIN")
                        .in(ProductImage::getProductId, productIds))
                .stream()
                .collect(Collectors.toMap(
                        ProductImage::getProductId,
                        ProductImage::getUrl,
                        (existing, replacement) -> existing
                ));
        List<ProductListItem> items = p.getRecords().stream().map(prod ->
                ProductListItem.builder()
                        .productId(prod.getId()).name(prod.getName())
                        .price(prod.getPrice()).status(prod.getStatus())
                        .mainImage(mainImages.get(prod.getId()))
                        .build()
        ).collect(Collectors.toList());

        return PageResult.<ProductListItem>builder()
                .total(p.getTotal()).page(page).size(size).items(items).build();
    }

    @Override
    public ProductDetailResp getProductDetail(Long productId) {
        Product p = productMapper.selectById(productId);
        boolean isAdmin = UserContext.isAdmin();
        if (p == null || ("DELETED".equals(p.getStatus()) && !isAdmin)
                || ("OFF_SALE".equals(p.getStatus()) && !isAdmin))
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);

        Category cat = categoryMapper.selectById(p.getCategoryId());
        List<ProductImage> imgs = imageMapper.selectList(
                new LambdaQueryWrapper<ProductImage>().eq(ProductImage::getProductId, productId)
                        .orderByAsc(ProductImage::getType).orderByAsc(ProductImage::getSort));

        return ProductDetailResp.builder()
                .productId(p.getId()).categoryId(p.getCategoryId())
                .categoryName(cat != null ? cat.getName() : null)
                .name(p.getName()).description(p.getDescription())
                .price(p.getPrice()).status(p.getStatus())
                .images(imgs.stream().map(i -> ImageResp.builder()
                        .imageId(i.getId()).url(i.getUrl())
                        .type(i.getType()).sort(i.getSort()).build())
                        .collect(Collectors.toList()))
                .build();
    }

    @Override
    public Long addImage(Long productId, AddImageReq req) {
        getExistingProduct(productId);
        ProductImage img = new ProductImage();
        img.setProductId(productId);
        img.setUrl(req.getUrl());
        img.setType(req.getType() != null ? req.getType() : "DETAIL");
        img.setSort(req.getSort() != null ? req.getSort() : 0);
        imageMapper.insert(img);
        return img.getId();
    }

    @Override
    public void deleteImage(Long productId, Long imageId) {
        getExistingProduct(productId);
        ProductImage img = imageMapper.selectById(imageId);
        if (img == null || !img.getProductId().equals(productId))
            throw new BusinessException(ErrorCode.PRODUCT_IMAGE_NOT_FOUND);
        imageMapper.deleteById(imageId);
    }

    private Product getExistingProduct(Long productId) {
        Product p = productMapper.selectById(productId);
        if (p == null || "DELETED".equals(p.getStatus()))
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        return p;
    }
}
