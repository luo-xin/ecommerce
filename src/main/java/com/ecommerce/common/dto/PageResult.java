package com.ecommerce.common.dto;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data @Builder
public class PageResult<T> {
    private Long total;
    private Integer page;
    private Integer size;
    private List<T> items;
}
