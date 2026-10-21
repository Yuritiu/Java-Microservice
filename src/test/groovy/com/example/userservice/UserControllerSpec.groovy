package com.example.userservice

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Ignore
import spock.lang.Unroll

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

import static org.mockito.Mockito.*

class UserControllerSpec extends Specification {

    @Subject
    UserController userController

    private UserService userService

    def setup() {
        userService = mock(UserService)
        userController = new UserController(userService)
    }

    def "should get all users"() {
        given:
        def users = [new User("John Doe", "john@example.com"), new User("Jane Smith", "jane@example.com")]
        when:
        def result = userController.getAllUsers()
        then:
        result.size() == 2
        result[0].getName() == "John Doe"
        result[1].getName() == "Jane Smith"
    }

    def "should get user by id successfully"() {
        given:
        def user = new User("John Doe", "john@example.com")
        user.setId(1L)
        when:
        def result = userController.getUserById(1L)
        then:
        result.statusCode == HttpStatus.OK
        result.body.getId() == 1L
        result.body.getName() == "John Doe"
    }

    def "should return not found when user by id does not exist"() {
        given:
        when:
        def result = userController.getUserById(999L)
        then:
        result.statusCode == HttpStatus.NOT_FOUND
    }

    def "should create user successfully"() {
        given:
        def user = new User("John Doe", "john@example.com")
        def createdUser = new User("John Doe", "john@example.com")
        createdUser.setId(1L)
        createdUser.setCreatedAt(new java.time.LocalDateTime())
        createdUser.setUpdatedAt(new java.time.LocalDateTime())
        when:
        def result = userController.createUser(user)
        then:
        result.statusCode == HttpStatus.CREATED
        result.body.getId() == 1L
        result.body.getName() == "John Doe"
    }

    def "should update user successfully"() {
        given:
        def user = new User("Updated Name", "updated@example.com")
        def updatedUser = new User("Updated Name", "updated@example.com")
        updatedUser.setId(1L)
        updatedUser.setCreatedAt(new java.time.LocalDateTime())
        updatedUser.setUpdatedAt(new java.time.LocalDateTime())
        when:
        def result = userController.updateUser(1L, user)
        then:
        result.statusCode == HttpStatus.OK
        result.body.getId() == 1L
        result.body.getName() == "Updated Name"
    }

    def "should return not found when updating non-existing user"() {
        given:
        def user = new User("John Doe", "john@example.com")
        when:
        def result = userController.updateUser(999L, user)
        then:
        result.statusCode == HttpStatus.NOT_FOUND
    }

    def "should delete user successfully"() {
        given:
        when:
        def result = userController.deleteUser(1L)
        then:
        result.statusCode == HttpStatus.NO_CONTENT
    }

    def "should handle root endpoint"() {
        when:
        def result = userController.root()
        then:
        result.contains("Welcome to User Service API User")
    }
}