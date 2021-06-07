package ru.fomin;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.net.URL;
import java.util.ResourceBundle;

public class RegController implements Initializable {

    @FXML
    public TextField loginFiled;
    @FXML
    public PasswordField passwordFiled;
    @FXML
    public PasswordField repeatFiled;
    @FXML
    public Label message;

    Controller controller;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

    }

    /**
     * Метод обработки нажатия кнопки регистрации
     * @param actionEvent обрабатываемое событие
     */
    public void registration(ActionEvent actionEvent) {
        String login = loginFiled.getText().trim();
        String password = passwordFiled.getText().trim();
        String repeatPassword = repeatFiled.getText().trim();

        if("".equals(login)){
            message.setText("Поле \"Логин\" обязятально для заполнения");
            return;
        }

        if("".equals(password)){
            message.setText("Поле \"Пароль\" обязятально для заполнения");
            return;
        }

        if(!password.equals(repeatPassword)){
            message.setText("Пароли не совпадают");
            return;
        }

        String response = controller.registration(login, password);
        message.setText(response);
    }

    /**
     *
     * Метод привязки главного контроллера к контроллеру регистрации
     *
     * @param controller привзываемый главный контроллер
     */
    public void setMainController(Controller controller) {
        this.controller = controller;
    }
}
