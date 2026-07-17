package com.xeom.grabbackend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "trips")
public class Trip {
    @Id
    private String id;

    @Column(nullable = false)
    private String customerId;

    private String driverId;
    private Double price;
    private String timeStart;
    private String timeEnd;
    private String addressStart;
    private String addressEnd;
    private Double latitudeStart;
    private Double longitudeStart;
    private Double latitudeEnd;
    private Double longitudeEnd;
    private Integer distance;

   // @Column(nullable = false)
    private String status;

    private String createdAt;
    private String updatedAt;

    public Trip() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public String getTimeStart() { return timeStart; }
    public void setTimeStart(String timeStart) { this.timeStart = timeStart; }
    public String getTimeEnd() { return timeEnd; }
    public void setTimeEnd(String timeEnd) { this.timeEnd = timeEnd; }
    public Double getLatitudeStart() { return latitudeStart; }
    public void setLatitudeStart(Double latitudeStart) { this.latitudeStart = latitudeStart; }
    public Double getLongitudeStart() { return longitudeStart; }
    public void setLongitudeStart(Double longitudeStart) { this.longitudeStart = longitudeStart; }
    public Double getLatitudeEnd() { return latitudeEnd; }
    public void setLatitudeEnd(Double latitudeEnd) { this.latitudeEnd = latitudeEnd; }
    public Double getLongitudeEnd() { return longitudeEnd; }
    public void setLongitudeEnd(Double longitudeEnd) { this.longitudeEnd = longitudeEnd; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public Integer getDistance() {
        return distance;
    }

    public void setDistance(Integer distance) {
        this.distance = distance;
    }

    public String getAddressStart() {
        return addressStart;
    }

    public void setAddressStart(String addressStart) {
        this.addressStart = addressStart;
    }

    public String getAddressEnd() {
        return addressEnd;
    }

    public void setAddressEnd(String addressEnd) {
        this.addressEnd = addressEnd;
    }
    
}
