package com.xeom.grabbackend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "drivers")
public class Driver {
    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String vehicle;

    @Column(nullable = false)
    private String plate;
    private Double lat;
    private Double lng;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private Double rating;
    private String createdAt;
    private String updatedAt;
    private String locationUpdatedAt;
    private String statusUpdatedAt;
    private String fcmToken;

    public Driver() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getVehicle() { return vehicle; }
    public void setVehicle(String vehicle) { this.vehicle = vehicle; }
    public String getPlate() { return plate; }
    public void setPlate(String plate) { this.plate = plate; }
    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }
    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public String getLocationUpdatedAt() { return locationUpdatedAt; }
    public void setLocationUpdatedAt(String locationUpdatedAt) { this.locationUpdatedAt = locationUpdatedAt; }
    public String getStatusUpdatedAt() { return statusUpdatedAt; }
    public void setStatusUpdatedAt(String statusUpdatedAt) { this.statusUpdatedAt = statusUpdatedAt; }
    public String getFcmToken() { return fcmToken; }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }
}
