package com.example.userservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final int maxUsers;

    // Constructor injection: dependencies are explicit and final, and tests can
    // simply call new UserService(mockRepository, 0).
    public UserService(UserRepository userRepository,
                       @Value("${demo.max-users:0}") int maxUsers) {
        this.userRepository = userRepository;
        this.maxUsers = maxUsers;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // "unless" stops a miss from being cached, so a user created later with
    // this id is found straight away rather than after the cache expires.
    @Cacheable(value = "users", key = "#id", unless = "#result == null")
    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    public User createUser(User user) {
        // maxUsers <= 0 means no limit (local dev). The public demo sets a cap.
        if (maxUsers > 0 && userRepository.count() >= maxUsers) {
            throw new DemoLimitReachedException(maxUsers);
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new DuplicateEmailException(user.getEmail());
        }
        // Never trust an id from the client: save() with an existing id would
        // overwrite that user instead of creating a new one.
        user.setId(null);
        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return userRepository.save(user);
    }

    @CacheEvict(value = "users", key = "#id")
    public User updateUser(Long id, User userDetails) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        boolean emailChanged = !user.getEmail().equals(userDetails.getEmail());
        if (emailChanged && userRepository.existsByEmail(userDetails.getEmail())) {
            throw new DuplicateEmailException(userDetails.getEmail());
        }

        user.setName(userDetails.getName());
        user.setEmail(userDetails.getEmail());
        user.setUpdatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }

    @CacheEvict(value = "users", key = "#id")
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException(id);
        }
        userRepository.deleteById(id);
    }
}
