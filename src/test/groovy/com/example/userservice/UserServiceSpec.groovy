package com.example.userservice

import spock.lang.Specification
import spock.lang.Shared
import spock.lang.Subject
import spock.lang.Unroll
import spock.lang.Ignore

import static org.mockito.Mockito.*
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.*

class UserServiceSpec extends Specification {

    @Subject
    UserService userService

    private UserRepository userRepository

    def setup() {
        userRepository = mock(UserRepository)
        userService = new UserService(userRepository)
    }

    def "should find all users"() {
        given:
        def users = [new User("John Doe", "john@example.com"), new User("Jane Smith", "jane@example.com")]
        when:
        def result = userService.getAllUsers()
        then:
        result == users
    }

    def "should find user by id"() {
        given:
        def user = new User("John Doe", "john@example.com")
        user.setId(1L)
        when:
        def result = userService.getUserById(1L)
        then:
        result.isPresent()
        result.get().getId() == 1L
        result.get().getName() == "John Doe"
    }

    def "should create user successfully"() {
        given:
        def user = new User("John Doe", "john@example.com")
        def savedUser = new User("John Doe", "john@example.com")
        savedUser.setId(1L)
        savedUser.setCreatedAt(new java.time.LocalDateTime())
        savedUser.setUpdatedAt(new java.time.LocalDateTime())
        when:
        def result = userService.createUser(user)
        then:
        result.getId() == 1L
        result.getName() == "John Doe"
        result.getEmail() == "john@example.com"
        result.getCreatedAt() != null
        result.getUpdatedAt() != null
    }

    def "should update user successfully"() {
        given:
        def existingUser = new User("John Doe", "john@example.com")
        existingUser.setId(1L)
        existingUser.setCreatedAt(new java.time.LocalDateTime())
        existingUser.setUpdatedAt(new java.time.LocalDateTime())
        def updatedUser = new User("Updated Name", "updated@example.com")
        
        when:
        def result = userService.updateUser(1L, updatedUser)
        then:
        result.getId() == 1L
        result.getName() == "Updated Name"
        result.getEmail() == "updated@example.com"
    }

    def "should delete user successfully"() {
        given:
        when:
        userService.deleteUser(1L)
        then:
        1 * userRepository.deleteById(1L)
    }

    def "should throw exception when trying to update non-existing user"() {
        given:
        def user = new User("John Doe", "john@example.com")
        when:
        userService.updateUser(1L, user)
        then:
        thrown(UserNotFoundException)
    }
}