package com.example.userservice

import spock.lang.Specification
import spock.lang.Subject

class UserServiceSpec extends Specification {

    UserRepository userRepository = Mock()

    @Subject
    UserService userService = new UserService(userRepository, 0)

    private static User user(Long id, String name, String email) {
        def user = new User(name, email)
        user.id = id
        return user
    }

    def "getAllUsers returns every user from the repository"() {
        given:
        def users = [user(1L, "John Doe", "john@example.com"), user(2L, "Jane Smith", "jane@example.com")]

        when:
        def result = userService.getAllUsers()

        then:
        1 * userRepository.findAll() >> users
        result == users
    }

    def "getUserById returns the user when it exists"() {
        when:
        def result = userService.getUserById(1L)

        then:
        1 * userRepository.findById(1L) >> Optional.of(user(1L, "John Doe", "john@example.com"))
        result.isPresent()
        result.get().name == "John Doe"
    }

    def "getUserById returns empty when the user does not exist"() {
        when:
        def result = userService.getUserById(999L)

        then:
        1 * userRepository.findById(999L) >> Optional.empty()
        result.isEmpty()
    }

    def "createUser sets timestamps and saves the user"() {
        given:
        def newUser = new User("John Doe", "john@example.com")

        when:
        def result = userService.createUser(newUser)

        then:
        1 * userRepository.existsByEmail("john@example.com") >> false
        1 * userRepository.save(_ as User) >> { User saved -> saved.id = 1L; saved }
        result.id == 1L
        result.createdAt != null
        result.updatedAt != null
    }

    def "createUser ignores an id sent by the client so it cannot overwrite another user"() {
        given:
        def newUser = user(42L, "John Doe", "john@example.com")

        when:
        userService.createUser(newUser)

        then:
        1 * userRepository.existsByEmail(_) >> false
        1 * userRepository.save({ it.id == null }) >> { User saved -> saved }
    }

    def "createUser rejects an email that is already in use"() {
        when:
        userService.createUser(new User("John Doe", "john@example.com"))

        then:
        1 * userRepository.existsByEmail("john@example.com") >> true
        0 * userRepository.save(_)
        thrown(DuplicateEmailException)
    }

    def "createUser refuses new users once the demo limit is reached"() {
        given:
        def limitedService = new UserService(userRepository, 2)

        when:
        limitedService.createUser(new User("John Doe", "john@example.com"))

        then:
        1 * userRepository.count() >> 2L
        0 * userRepository.save(_)
        thrown(DemoLimitReachedException)
    }

    def "createUser allows new users while under the demo limit"() {
        given:
        def limitedService = new UserService(userRepository, 2)

        when:
        limitedService.createUser(new User("John Doe", "john@example.com"))

        then:
        1 * userRepository.count() >> 1L
        1 * userRepository.existsByEmail(_) >> false
        1 * userRepository.save(_ as User) >> { User saved -> saved }
    }

    def "updateUser changes the name and email"() {
        given:
        def existing = user(1L, "John Doe", "john@example.com")

        when:
        def result = userService.updateUser(1L, new User("Updated Name", "updated@example.com"))

        then:
        1 * userRepository.findById(1L) >> Optional.of(existing)
        1 * userRepository.existsByEmail("updated@example.com") >> false
        1 * userRepository.save(existing) >> existing
        result.id == 1L
        result.name == "Updated Name"
        result.email == "updated@example.com"
    }

    def "updateUser allows keeping the same email"() {
        given:
        def existing = user(1L, "John Doe", "john@example.com")

        when:
        userService.updateUser(1L, new User("New Name", "john@example.com"))

        then:
        1 * userRepository.findById(1L) >> Optional.of(existing)
        0 * userRepository.existsByEmail(_)
        1 * userRepository.save(existing) >> existing
    }

    def "updateUser rejects changing to an email another user has"() {
        when:
        userService.updateUser(1L, new User("John Doe", "taken@example.com"))

        then:
        1 * userRepository.findById(1L) >> Optional.of(user(1L, "John Doe", "john@example.com"))
        1 * userRepository.existsByEmail("taken@example.com") >> true
        0 * userRepository.save(_)
        thrown(DuplicateEmailException)
    }

    def "updateUser throws when the user does not exist"() {
        when:
        userService.updateUser(999L, new User("John Doe", "john@example.com"))

        then:
        1 * userRepository.findById(999L) >> Optional.empty()
        thrown(UserNotFoundException)
    }

    def "deleteUser deletes an existing user"() {
        when:
        userService.deleteUser(1L)

        then:
        1 * userRepository.existsById(1L) >> true
        1 * userRepository.deleteById(1L)
    }

    def "deleteUser throws when the user does not exist"() {
        when:
        userService.deleteUser(999L)

        then:
        1 * userRepository.existsById(999L) >> false
        0 * userRepository.deleteById(_)
        thrown(UserNotFoundException)
    }
}
