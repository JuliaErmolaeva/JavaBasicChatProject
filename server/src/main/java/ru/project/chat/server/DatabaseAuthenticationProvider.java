package ru.project.chat.server;

import ru.project.chat.server.model.Role;
import ru.project.chat.server.model.RoleName;
import ru.project.chat.server.model.User;

import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import static ru.project.chat.server.model.RoleName.ADMIN;

public class DatabaseAuthenticationProvider implements AuthenticationProvider {

    // кэш пользователей
    private final List<User> users;

    private static final String SELECT_ALL_USERS = "SELECT u.login login, u.nickname nickname, u.password password, " +
            "u.start_ban_date_time, u.end_ban_date_time, ur.role_id role_id " +
            "FROM public.user u " +
            "JOIN public.user_role ur ON u.id = ur.user_id ";

    private static final String SELECT_ALL_ROLES = "SELECT r.id id, r.name role_name FROM public.role r ";

    private static final String SELECT_CURRVAL_USER_ID = "SELECT currval('user_id_seq')";

    private static final String INSERT_INTO_USER = "INSERT INTO public.user (id, login, password, nickname) " +
            "VALUES(nextval('user_id_seq'), ?, ?, ?)";

    private static final String INSERT_INTO_USER_ROLE = "INSERT INTO public.user_role (user_id, role_id) VALUES (?, ?)";

    private static final String UPDATE_USER_FOR_TEMPORARY_BAN = "UPDATE public.user " +
            "SET start_ban_date_time = ? , end_ban_date_time = ? " +
            "WHERE nickname = ?";

    public DatabaseAuthenticationProvider() throws SQLException {
        this.users = Collections.synchronizedList(new ArrayList<>());

        Map<User, Set<Integer>> userSetRoleId = new HashMap<>();

        try (Connection connection = ConnectorDB.getConnection();
             Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery(SELECT_ALL_USERS)) {
                while (rs.next()) {
                    User user = new User();
                    user.setLogin(rs.getString("login"));
                    user.setNickname(rs.getString("nickname"));
                    user.setPassword(rs.getString("password"));

                    Timestamp sqlStartBanDateTime = rs.getTimestamp("start_ban_date_time");
                    if (sqlStartBanDateTime != null) {
                        user.setStartBanDateTime(sqlStartBanDateTime.toLocalDateTime());
                    }

                    Timestamp sqlEndBanDateTime = rs.getTimestamp("end_ban_date_time");
                    if (sqlEndBanDateTime != null) {
                        user.setEndBanDateTime(sqlEndBanDateTime.toLocalDateTime());
                    }

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
                Set<Role> roles = user.getRoles();
                roles.add(rolesMap.get(id));
                user.setRoles(roles);
            }
            users.add(user);
        }
        System.out.println("Загрузили кеш пользователей");
    }

    @Override
    public String getNicknameByLoginAndPassword(String login, String password) {
        for (User user : users) {
            if (Objects.equals(user.getPassword(), password) && Objects.equals(user.getLogin(), login)) {
                return user.getNickname();
            }
        }
        return null;
    }

    @Override
    public boolean register(String login, String password, String nickname) {
        for (User user : users) {
            if (Objects.equals(user.getNickname(), nickname) || Objects.equals(user.getLogin(), login)) {
                return false;
            }
        }

        Role role = new Role(2, RoleName.USER);
        users.add(new User(login, password, nickname, role));

        Integer currValUserId;
        try (Connection connection = ConnectorDB.getConnection();
             PreparedStatement prepareStatement = connection.prepareStatement(INSERT_INTO_USER)) {

            prepareStatement.setString(1, login);
            prepareStatement.setString(2, password);
            prepareStatement.setString(3, nickname);

            int affectedRows = prepareStatement.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Creating user failed, no rows affected.");
            }

            System.out.println("В таблицу user добавлена " + affectedRows + " запись/записей");

            currValUserId = getCurrVal(connection);

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
    public void banUser(String nicknameForBan, long minutesBan) {
        for (User user : users) {
            if (nicknameForBan.equals(user.getNickname())) {
                try {
                    LocalDateTime startBanDateTime = LocalDateTime.now();

                    LocalDateTime endBanDateTime;
                    int affectedRows;
                    if (minutesBan == 0) {
                        endBanDateTime = LocalDateTime.MAX; // время для перманентного бана
                    } else {
                        endBanDateTime = startBanDateTime.plusMinutes(minutesBan);
                    }

                    affectedRows = updateUserForBan(nicknameForBan, startBanDateTime, endBanDateTime);

                    user.setStartBanDateTime(startBanDateTime);
                    user.setEndBanDateTime(endBanDateTime);

                    if (affectedRows == 0) {
                        throw new SQLException("Updating user failed, no rows affected.");
                    } else {
                        // TODO: написать какого пользователя
                        System.out.println("Успешно забанили пользователя");
                    }
                    // TODO: сделать отправку пользователю что его забанили
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }


    private int updateUserForBan(String nicknameForBan,
                                 LocalDateTime startBanDateTime,
                                 LocalDateTime endBanDateTime) throws SQLException {
        try (Connection connection = ConnectorDB.getConnection();
             PreparedStatement prepareStatement = connection.prepareStatement(UPDATE_USER_FOR_TEMPORARY_BAN)) {

            Timestamp sqlStartBanDateTime = Timestamp.valueOf(startBanDateTime);
            Timestamp sqlEndBanDateTime = Timestamp.valueOf(endBanDateTime);

            prepareStatement.setTimestamp(1, sqlStartBanDateTime);
            prepareStatement.setTimestamp(2, sqlEndBanDateTime);
            prepareStatement.setString(3, nicknameForBan);

            return prepareStatement.executeUpdate();
        }
    }

    @Override
    public long getMinutesUntilTheEndBan(String nickname) {
        for (User user : users) {
            if (nickname.equals(user.getNickname()) && user.getEndBanDateTime() != null) {
                return Duration.between(LocalDateTime.now(), user.getEndBanDateTime()).toMinutes();
            }
        }
        return 0;
    }

    @Override
    public boolean isCurrentUserAdmin(String nickname) {
        for (User user : users) {
            if (nickname.equals(user.getNickname()) && user.getRoles().contains(new Role(1, ADMIN))) {
                return true;
            }
        }
        return false;
    }

    private Integer getCurrVal(Connection connection) {
        Integer nextID_from_seq = null;
        try (Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery(SELECT_CURRVAL_USER_ID)) {
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