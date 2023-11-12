package ru.project.chat.server.model;

import lombok.Setter;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Setter
public class User {
    private String login;
    private String password;
    private String username;
    private Set<Role> roles;

    public User() {
    }

    public User(String login, String password, String username, Role role) {
        this.login = login;
        this.password = password;
        this.username = username;
        this.roles = Collections.singleton(role);
    }

    public String getLogin() {
        return login;
    }

    public String getPassword() {
        return password;
    }

    public String getUsername() {
        return username;
    }

    public Set<Role> getRoles() {
        if (roles == null) {
            return new HashSet<>();
        }
        return roles;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(login, user.login) && Objects.equals(password, user.password) && Objects.equals(username, user.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(login, password, username);
    }
}