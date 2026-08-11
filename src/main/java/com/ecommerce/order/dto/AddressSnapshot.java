package com.ecommerce.order.dto;
import lombok.Data;

@Data
public class AddressSnapshot {
    private String receiverName;
    private String receiverPhone;
    private String province;
    private String city;
    private String district;
    private String detail;
}
