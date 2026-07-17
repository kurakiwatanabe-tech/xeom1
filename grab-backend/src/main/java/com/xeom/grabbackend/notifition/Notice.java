package com.xeom.grabbackend.notifition;

import lombok.Data;

@Data
public class Notice {
    private Long customerId;
    private Byte type;
    private Double latitude;
    private Double longitude;
}
