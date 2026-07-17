package com.xeom.grabbackend.controller;

import com.xeom.grabbackend.model.Customer;
import com.xeom.grabbackend.service.RideDataService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.lang.NonNull;
import java.util.Objects;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/customers")
@CrossOrigin(origins = "*")
public class CustomerController {
    private final RideDataService rideDataService;

    public CustomerController(RideDataService rideDataService) {
        this.rideDataService = rideDataService;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(name = "q", required = false) String q) {
        List<Customer> list = rideDataService.listCustomers();
        if (q != null && !q.isBlank()) {
            String keyword = q.toLowerCase();
            list = list.stream().filter(c ->
                    (c.getName() != null && c.getName().toLowerCase().contains(keyword)) ||
                    (c.getPhone() != null && c.getPhone().contains(keyword)) ||
                    (c.getEmail() != null && c.getEmail().toLowerCase().contains(keyword))
            ).toList();
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("count", list.size());
        response.put("data", list);
        return response;
    }

    @GetMapping("/{id}")
    public Customer getById(@PathVariable("id") @NonNull String id) {
        return findCustomer(id).orElseThrow(() -> new NotFoundException("Không tìm thấy khách hàng"));
    }

    @GetMapping("/phone/{phone}")
    public Customer getByPhone(@PathVariable("phone") @NonNull String phone) {
        return findCustomerByPhone(phone).orElseThrow(() -> new NotFoundException("Không tìm thấy khách hàng"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Customer create(@RequestBody Customer payload) {
        if (payload.getName() == null || payload.getName().isBlank() || payload.getPhone() == null || payload.getPhone().isBlank()) {
            throw new BadRequestException("Thiếu trường bắt buộc: name, phone");
        }
        Customer customer = new Customer();
        customer.setId("cust_" + Instant.now().toEpochMilli());
        customer.setName(payload.getName());
        customer.setPhone(payload.getPhone());
        customer.setEmail(payload.getEmail());
        customer.setAddress(payload.getAddress());
        customer.setCreatedAt(Instant.now().toString());
        return rideDataService.saveCustomer(customer);
    }

    @PutMapping("/{id}")
    public Customer update(@PathVariable("id") @NonNull String id, @RequestBody Customer payload) {
        Customer existing = findCustomer(id).orElseThrow(() -> new NotFoundException("Không tìm thấy khách hàng"));
        if (payload.getName() != null) existing.setName(payload.getName());
        if (payload.getPhone() != null) existing.setPhone(payload.getPhone());
        if (payload.getEmail() != null) existing.setEmail(payload.getEmail());
        if (payload.getAddress() != null) existing.setAddress(payload.getAddress());
        existing.setUpdatedAt(Instant.now().toString());
        return rideDataService.saveCustomer(existing);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable("id") @NonNull String id) {
        Customer match = findCustomer(id).orElseThrow(() -> new NotFoundException("Không tìm thấy khách hàng"));
        rideDataService.deleteCustomer(Objects.requireNonNull(id));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("deleted", true);
        response.put("customer", match);
        return response;
    }

    private Optional<Customer> findCustomer(String id) {
        return rideDataService.findCustomer(Objects.requireNonNull(id));
    }

    private Optional<Customer> findCustomerByPhone(String phone) {
        return rideDataService.findCustomerByPhone(Objects.requireNonNull(phone));
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(NotFoundException.class)
    public Map<String, String> handleNotFound(NotFoundException ex) {
        return Map.of("error", ex.getMessage());
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(BadRequestException.class)
    public Map<String, String> handleBadRequest(BadRequestException ex) {
        return Map.of("error", ex.getMessage());
    }

    private static class NotFoundException extends RuntimeException {
        private NotFoundException(String message) { super(message); }
    }

    private static class BadRequestException extends RuntimeException {
        private BadRequestException(String message) { super(message); }
    }
}
