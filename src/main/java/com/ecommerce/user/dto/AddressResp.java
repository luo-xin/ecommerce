package com.ecommerce.user.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AddressResp {
    private Long addressId;
    private String receiverName;
    private String receiverPhone;
    private String province;
    private String city;
    private String district;
    private String detail;
    private Boolean isDefault;
}
