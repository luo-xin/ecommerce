package com.ecommerce.product.dto;
import lombok.Data;

@Data
public class AddImageReq {
    private String url;
    private String type;   // MAIN or DETAIL
    private Integer sort;
}
