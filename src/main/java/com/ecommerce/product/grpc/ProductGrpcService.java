package com.ecommerce.product.grpc;

import com.ecommerce.common.BusinessException;
import com.ecommerce.common.dto.PageResult;
import com.ecommerce.grpc.proto.*;
import com.ecommerce.product.dto.CategoryTreeResp;
import com.ecommerce.product.dto.ProductDetailResp;
import com.ecommerce.product.dto.ProductListItem;
import com.ecommerce.product.service.CategoryService;
import com.ecommerce.product.service.ProductService;
import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.List;
import java.util.stream.Collectors;

@GrpcService
@RequiredArgsConstructor
public class ProductGrpcService extends ProductServiceGrpc.ProductServiceImplBase {

    private final ProductService productService;
    private final CategoryService categoryService;

    @Override
    public void listProducts(ListProductsReq req, StreamObserver<ListProductsResp> responseObserver) {
        try {
            Long categoryId = req.getCategoryId() == 0 ? null : req.getCategoryId();
            String keyword = req.getKeyword().isBlank() ? null : req.getKeyword();
            int size = req.getSize() == 0 ? 20 : Math.min(req.getSize(), 100);

            PageResult<ProductListItem> result = productService.listProducts(categoryId, keyword, req.getPage(), size);

            List<GrpcProductItem> items = result.getItems().stream()
                    .map(p -> GrpcProductItem.newBuilder()
                            .setId(p.getProductId())
                            .setName(p.getName() != null ? p.getName() : "")
                            .setPrice(p.getPrice() != null ? p.getPrice().toPlainString() : "0")
                            .setMainImage(p.getMainImage() != null ? p.getMainImage() : "")
                            .setStatus(p.getStatus() != null ? p.getStatus() : "")
                            .build())
                    .collect(Collectors.toList());

            responseObserver.onNext(ListProductsResp.newBuilder()
                    .addAllProducts(items)
                    .setTotal(result.getTotal())
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getProduct(GetProductReq req, StreamObserver<GrpcProductDetail> responseObserver) {
        try {
            ProductDetailResp detail = productService.getProductDetail(req.getProductId());

            List<String> imageUrls = detail.getImages() != null
                    ? detail.getImages().stream()
                        .map(img -> img.getUrl() != null ? img.getUrl() : "")
                        .collect(Collectors.toList())
                    : List.of();

            responseObserver.onNext(GrpcProductDetail.newBuilder()
                    .setId(detail.getProductId())
                    .setName(detail.getName() != null ? detail.getName() : "")
                    .setPrice(detail.getPrice() != null ? detail.getPrice().toPlainString() : "0")
                    .setDescription(detail.getDescription() != null ? detail.getDescription() : "")
                    .addAllImages(imageUrls)
                    .setCategoryId(detail.getCategoryId() != null ? detail.getCategoryId() : 0)
                    .setStatus(detail.getStatus() != null ? detail.getStatus() : "")
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void listCategories(Empty req, StreamObserver<ListCategoriesResp> responseObserver) {
        try {
            List<CategoryTreeResp> tree = categoryService.getCategoryTree();
            List<GrpcCategoryItem> grpcItems = tree.stream()
                    .map(this::toGrpcCategory)
                    .collect(Collectors.toList());

            responseObserver.onNext(ListCategoriesResp.newBuilder()
                    .addAllCategories(grpcItems)
                    .build());
            responseObserver.onCompleted();
        } catch (BusinessException e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private GrpcCategoryItem toGrpcCategory(CategoryTreeResp c) {
        GrpcCategoryItem.Builder builder = GrpcCategoryItem.newBuilder()
                .setId(c.getCategoryId())
                .setName(c.getName() != null ? c.getName() : "");
        if (c.getChildren() != null) {
            c.getChildren().stream().map(this::toGrpcCategory).forEach(builder::addChildren);
        }
        return builder.build();
    }
}
