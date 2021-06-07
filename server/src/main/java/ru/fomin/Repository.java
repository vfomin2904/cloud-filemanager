package ru.fomin;

import java.sql.*;

public class Repository {
    final Connection connect;
    static Repository repository;

    private Repository() throws SQLException {
        connect = DriverManager.getConnection("jdbc:mysql://localhost:3306/filemanager?currentSchema=filemanager", "root", "root");
    }

    /**
     * Метод добавления нвого пользователя в базу данных
     * @param login логин пользователя
     * @param password пароль пользователя
     * @return true - пользователь добавлен, false - не добавлен
     * @throws SQLException
     */
    public boolean addUser(String login, String password) throws SQLException {
        if (!findUser(login)) {
            PreparedStatement stm = connect.prepareStatement("INSERT INTO users (`login`, `password`) VALUES (?, ?)");
            stm.setString(1, login);
            stm.setString(2, password);
            stm.executeUpdate();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Поиск пользователя по логину и паролю
     * @param login логин пользователя
     * @param password пароль пользователя
     * @return true - пользователь найден, false - не найден
     * @throws SQLException
     */
    public boolean findUser(String login, String password) throws SQLException {
        PreparedStatement stm = connect.prepareStatement("SELECT count(*) FROM users WHERE login = ? AND password = ?");
        stm.setString(1, login);
        stm.setString(2, password);
        ResultSet result = stm.executeQuery();
        result.next();
        return result.getInt(1) > 0;
    }

    /**
     * Поиск пользователя по логину
     * @param login логин пользователя
     * @return true - пользователь найден, false - не найден
     * @throws SQLException
     */
    public boolean findUser(String login) throws SQLException {
        PreparedStatement stm = connect.prepareStatement("SELECT count(*) FROM users WHERE login = ?");
        stm.setString(1, login);
        ResultSet result = stm.executeQuery();
        result.next();
        return result.getInt(1) > 0;
    }

    /**
     * Метод, гарантирующий единственность экземпляра класса Repository
     * @return объект класса Repository
     */
    public static Repository getRepository() {
        if (repository == null) {
            try {
                repository = new Repository();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
        return repository;
    }
}
