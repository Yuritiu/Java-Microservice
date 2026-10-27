package com.example.userservice

import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put

/**
 * Sends real HTTP requests through Spring MVC (JSON parsing, validation and
 * GlobalExceptionHandler included), with the service mocked out.
 * No database or Spring context needed.
 */
class UserControllerSpec extends Specification {

    UserService userService = Mock()

    MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new UserController(userService), new RootController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build()

    private static User user(Long id, String name, String email) {
        def user = new User(name, email)
        user.id = id
        return user
    }

    private def postJson(String url, String json) {
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json)).andReturn().response
    }

    private def putJson(String url, String json) {
        mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(json)).andReturn().response
    }

    def "GET /api/users returns all users"() {
        when:
        def response = mvc.perform(get("/api/users")).andReturn().response

        then:
        1 * userService.getAllUsers() >> [user(1L, "John Doe", "john@example.com"), user(2L, "Jane Smith", "jane@example.com")]
        response.status == 200
        response.contentAsString.contains('"name":"John Doe"')
        response.contentAsString.contains('"name":"Jane Smith"')
    }

    def "GET /api/users/{id} returns the user"() {
        when:
        def response = mvc.perform(get("/api/users/1")).andReturn().response

        then:
        1 * userService.getUserById(1L) >> Optional.of(user(1L, "John Doe", "john@example.com"))
        response.status == 200
        response.contentAsString.contains('"id":1')
    }

    def "GET /api/users/{id} returns 404 when the user does not exist"() {
        when:
        def response = mvc.perform(get("/api/users/999")).andReturn().response

        then:
        1 * userService.getUserById(999L) >> Optional.empty()
        response.status == 404
        response.contentAsString.contains("User not found")
    }

    def "POST /api/users creates a user and returns 201"() {
        when:
        def response = postJson("/api/users", '{"name":"John Doe","email":"john@example.com"}')

        then:
        1 * userService.createUser({ it.name == "John Doe" && it.email == "john@example.com" }) >> user(1L, "John Doe", "john@example.com")
        response.status == 201
        response.contentAsString.contains('"id":1')
    }

    def "POST /api/users ignores an id in the request body"() {
        when:
        postJson("/api/users", '{"id":5,"name":"John Doe","email":"john@example.com"}')

        then:
        1 * userService.createUser({ it.id == null }) >> user(1L, "John Doe", "john@example.com")
    }

    def "POST /api/users returns 400 with field errors for invalid input"() {
        when:
        def response = postJson("/api/users", '{"name":"","email":"not-an-email"}')

        then:
        0 * userService.createUser(_)
        response.status == 400
        response.contentAsString.contains("name")
        response.contentAsString.contains("email")
    }

    def "POST /api/users returns 400 for malformed JSON"() {
        when:
        def response = postJson("/api/users", '{"name":')

        then:
        0 * userService.createUser(_)
        response.status == 400
    }

    def "POST /api/users returns 409 when the email is taken"() {
        when:
        def response = postJson("/api/users", '{"name":"John Doe","email":"john@example.com"}')

        then:
        1 * userService.createUser(_) >> { throw new DuplicateEmailException("john@example.com") }
        response.status == 409
    }

    def "POST /api/users returns 429 when the demo is full"() {
        when:
        def response = postJson("/api/users", '{"name":"John Doe","email":"john@example.com"}')

        then:
        1 * userService.createUser(_) >> { throw new DemoLimitReachedException(100) }
        response.status == 429
    }

    def "PUT /api/users/{id} updates the user"() {
        when:
        def response = putJson("/api/users/1", '{"name":"Updated Name","email":"updated@example.com"}')

        then:
        1 * userService.updateUser(1L, _) >> user(1L, "Updated Name", "updated@example.com")
        response.status == 200
        response.contentAsString.contains('"name":"Updated Name"')
    }

    def "PUT /api/users/{id} returns 404 when the user does not exist"() {
        when:
        def response = putJson("/api/users/999", '{"name":"John Doe","email":"john@example.com"}')

        then:
        1 * userService.updateUser(999L, _) >> { throw new UserNotFoundException(999L) }
        response.status == 404
    }

    def "DELETE /api/users/{id} returns 204"() {
        when:
        def response = mvc.perform(delete("/api/users/1")).andReturn().response

        then:
        1 * userService.deleteUser(1L)
        response.status == 204
    }

    def "DELETE /api/users/{id} returns 404 when the user does not exist"() {
        when:
        def response = mvc.perform(delete("/api/users/999")).andReturn().response

        then:
        1 * userService.deleteUser(999L) >> { throw new UserNotFoundException(999L) }
        response.status == 404
    }

    def "GET / redirects to the Swagger UI page"() {
        when:
        def response = mvc.perform(get("/")).andReturn().response

        then:
        response.status == 302
        response.getHeader("Location") == "/swagger-ui/index.html"
    }
}
