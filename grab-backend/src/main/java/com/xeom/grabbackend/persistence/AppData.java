package com.xeom.grabbackend.persistence;

import com.xeom.grabbackend.model.Customer;
import com.xeom.grabbackend.model.Driver;
import com.xeom.grabbackend.model.Trip;

import java.util.ArrayList;
import java.util.List;

public class AppData {
    private List<Customer> customers = new ArrayList<>();
    private List<Driver> drivers = new ArrayList<>();
    private List<Trip> trips = new ArrayList<>();

    public List<Customer> getCustomers() { return customers; }
    public void setCustomers(List<Customer> customers) { this.customers = customers; }
    public List<Driver> getDrivers() { return drivers; }
    public void setDrivers(List<Driver> drivers) { this.drivers = drivers; }
    public List<Trip> getTrips() { return trips; }
    public void setTrips(List<Trip> trips) { this.trips = trips; }
}
