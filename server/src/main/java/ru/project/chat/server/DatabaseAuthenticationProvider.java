package ru.project.chat.server;

import ru.project.chat.server.model.Role;
import ru.project.chat.server.model.RoleName;
import ru.project.chat.server.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static ru.project.chat.server.model.RoleName.ADMIN;

public class DatabaseAuthenticationProvider implements AuthenticationProvider {

    // кэш пользователей
    private final List<User> users;

    private static final String SELECT_ALL_USERS = "SELECT u.login login, u.nickname nickname, u.password password, ur.role_id role_id " +
            "FROM public.user u " +
            "JOIN public.user_role ur ON u.id = ur.user_id ";

    private static final String SELECT_ALL_ROLES = "SELECT r.id id, r.name role_name FROM public.role r ";

    private static final String SELECT_CURRVAL_USER_ID = "SELECT currval('user_id_seq')";

    private static final String INSERT_INTO_USER = "INSERT INTO public.user (id, login, password, nickname) " +
            "VALUES(nextval('user_id_seq'), ?, ?, ?)";

    private static final String INSERT_INTO_USER_ROLE = "INSERT INTO public.user_role (user_id, role_id) VALUES (?, ?)";

    public DatabaseAuthenticationProvider() throws SQLException {
        this.users = new ArrayList<>();

        Map<User, Set<Integer>> userSetRoleId = new HashMap<>();

        try (Connection connection = ConnectorDB.getConnection();
             Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery(SELECT_ALL_USERS)) {
                while (rs.next()) {
                    User user = new User();
                    user.setLogin(rs.getString("login"));
                    user.setUsername(rs.getString("nickname"));
                    user.setPassword(rs.getString("password"));
                    int roleId = rs.getInt("role_id");
                    if (userSetRoleId.containsKey(user)) {
                        Set<Integer> roleIds = userSetRoleId.get(user);
                        roleIds.add(roleId);
                    } else {
                        Set<Integer> roleIds = new HashSet<>();
                        roleIds.add(roleId);
                        userSetRoleId.put(user, roleIds);
                    }
                }
            }
        }

        HashMap<Integer, Role> rolesMap = new HashMap<>();
        try (Connection connection = ConnectorDB.getConnection();
             Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery(SELECT_ALL_ROLES)) {
                while (rs.next()) {
                    Role role = new Role(
                            rs.getInt("id"),
                            RoleName.valueOf(rs.getString("role_name").toUpperCase())
                    );
                    rolesMap.put(role.getId(), role);
                }
            }
        }

        for (Map.Entry<User, Set<Integer>> userSetEntry : userSetRoleId.entrySet()) {
            User user = userSetEntry.getKey();
            for (Integer id : userSetEntry.getValue()) {
                user.getRoles().add(rolesMap.get(id));
            }
            users.add(user);
        }
    }

    @Override
    public String getUsernameByLoginAndPassword(String login, String password) {
        for (User user : users) {
            if (Objects.equals(user.getPassword(), password) && Objects.equals(user.getLogin(), login)) {
                return user.getUsername();
            }
        }
        return null;
    }

    @Override
    public boolean register(String login, String password, String username) {
        for (User user : users) {
            if (Objects.equals(user.getUsername(), username) || Objects.equals(user.getLogin(), login)) {
                return false;
            }
        }

        Role role = new Role(2, RoleName.USER);
        users.add(new User(login, password, username, role));

        Integer currValUserId;
        try (Connection connection = ConnectorDB.getConnection();
             PreparedStatement prepareStatement = connection.prepareStatement(INSERT_INTO_USER)) {

            prepareStatement.setString(1, login);
            prepareStatement.setString(2, password);
            prepareStatement.setString(3, username);

            int affectedRows = prepareStatement.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Creating user failed, no rows affected.");
            }

            System.out.println("В таблицу user добавлена " + affectedRows + " запись/записей");

            currValUserId = getCurrVal(connection, SELECT_CURRVAL_USER_ID);

            if (currValUserId == null) {
                throw new SQLException("Creating user failed, no ID obtained.");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        try (Connection connection = ConnectorDB.getConnection();
             PreparedStatement prepareStatement = connection.prepareStatement(INSERT_INTO_USER_ROLE)) {

            prepareStatement.setInt(1, currValUserId);
            prepareStatement.setInt(2, role.getId());

            int affectedRows = prepareStatement.executeUpdate();
            System.out.println("В таблицу user_role добавлена " + affectedRows + " запись/записей");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return true;
    }

    @Override
    public boolean isCurrentUserCanKick(String username) {
        for (User user : users) {
            if (username.equals(user.getUsername()) && user.getRoles().contains(new Role(1, ADMIN))) {
                return true;
            }
        }
        return false;
    }

    private Integer getCurrVal(Connection connection, String selectCurrVal) {
        Integer nextID_from_seq = null;
        try (Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery(selectCurrVal)) {
                if (rs.next()) {
                    nextID_from_seq = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return nextID_from_seq;
    }
}