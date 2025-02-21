package com.alibou.websocket.user;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;

    public void saveUser(User user) {


        Optional<User> existingUser = repository.findById(user.getId());

        if (existingUser.isPresent()) { 
          
            User foundUser = existingUser.get();
            foundUser.setStatus(Status.ONLINE);
            repository.save(foundUser);
            return;
        }
        user.setStatus(Status.ONLINE);

        
        if ("MANAGER".equalsIgnoreCase(user.getRole())) {
            user.setAssignedCustomers(new ArrayList<>()); // Ensure it's not null
            user.setAssignedManagerId(null); 
        } else if ("CUSTOMER".equalsIgnoreCase(user.getRole())) {
          
            if (user.getAssignedManagerId() == null) {
                Optional<User> manager = findManagerWithLeastCustomers();
                manager.ifPresent(m -> {
                    user.setAssignedManagerId(m.getId()); // Store only the manager's ID
                    m.getAssignedCustomers().add(user.getId()); // Store only customer ID
                    repository.save(m); // Update manager with new customer
                });
            }
        }
        repository.save(user);
    }

    public User saveDirectUser(User user) {
        Optional<User> existingUser = repository.findById(user.getId());
    
        if (existingUser.isPresent()) { 
            User foundUser = existingUser.get();
            foundUser.setStatus(Status.ONLINE);
            return repository.save(foundUser); // Return updated user
        }
    
        user.setStatus(Status.ONLINE);
        repository.save(user);
        return user;
    }
    
    
    public Optional<User> findManagerWithLeastCustomers() {
        List<User> managers = repository.findAllByRole("MANAGER");

        return managers.stream()
                .min((m1, m2) -> Integer.compare(m1.getAssignedCustomers().size(), m2.getAssignedCustomers().size()));
    }
    public void disconnect(User user) {
        var storedUser = repository.findById(user.getId()).orElse(null); // Changed nickName to id
        if (storedUser != null) {
            storedUser.setStatus(Status.OFFLINE);
            repository.save(storedUser);
        }
    }

    public List<User> findConnectedUsers() {
        return repository.findAllByStatus(Status.ONLINE);
    }

    public List<User> findALLConnectedUsers() {
        return repository.findAll();
    }
    



   public List<User> getCustomersForManager(String managerId) {
    // User manager = repository.findById(managerId)
    //                          .orElseThrow(() -> new RuntimeException("Manager not found"));
    User manager = repository.findById(managerId)
    .orElse(null);
       if (manager == null) {
         return new ArrayList<>(); // Or return a response with a meaningful message
        }

    // Validate if user is actually a MANAGER
    if (!"MANAGER".equalsIgnoreCase(manager.getRole())) {
        throw new RuntimeException("User is not a manager");
    }

    // Get assigned customer IDs safely
    List<String> assignedCustomerIds = 
        Optional.ofNullable(manager.getAssignedCustomers()).orElse(Collections.emptyList());

    // Fetch full customer objects
    return repository.findAllById(assignedCustomerIds);
    }
    

    public User getManagerForCustomer(String customerId) {
        User customer = repository.findById(customerId).orElse(null);
        if (customer != null && customer.getAssignedManagerId() != null) {
            return repository.findById(customer.getAssignedManagerId()).orElse(null);
        }
        return null;
    }

    public ResponseEntity<String> assignCustomerToManager(String managerId, String customerId) {
        Optional<User> managerOpt = repository.findById(managerId);
        Optional<User> customerOpt = repository.findById(customerId);
    
        if (managerOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Manager not found.");
        }
        if (customerOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Customer not found.");
        }
    
        User manager = managerOpt.get();
        User customer = customerOpt.get();
    
        // Ensure the user is a manager
        if (!"MANAGER".equalsIgnoreCase(manager.getRole())) {
            return ResponseEntity.badRequest().body("User is not a manager.");
        }
    
        // Assign the customer to the manager
        manager.getAssignedCustomers().add(customerId);
        customer.setAssignedManagerId(managerId);
    
        repository.save(manager);
        repository.save(customer);
    
        return ResponseEntity.ok("Customer assigned successfully.");
    }
    
    
}
