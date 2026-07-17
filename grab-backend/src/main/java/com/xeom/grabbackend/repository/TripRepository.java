package com.xeom.grabbackend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.xeom.grabbackend.model.Trip;

@Repository
public interface TripRepository extends JpaRepository<Trip, String> {
    List<Trip> findByDriverId(String driverId);
}
