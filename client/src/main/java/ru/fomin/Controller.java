package ru.fomin;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class Controller implements Initializable {


    @FXML
//    public TextField commandLine;
    public VBox clientWindow;
    public VBox serverWindow;
    public HBox fileArea;
    public VBox btnPanel;
    public Button uploadBtn;
    public Button downloadBtn;
    public Button removeBtn;

    //    private Client client;
    private final Socket socket;
    private final DataOutputStream out;
    private final DataInputStream in;
    public TextField renameField;
    public TextField loginField;
    public PasswordField passwordField;
    public Label message;
    public VBox mainWindow;
    public VBox loginWindow;
    public ComboBox sortType;
    public HBox sortBox;
    private RegController regController;
    private Stage regStage;
    private boolean clientWindowFocused;
    File clientFiles;
    private String userLogin;
    private HashMap<File, Long> serverDirectorySizes = new HashMap<>();


    public Controller() throws IOException {
        socket = new Socket("localhost", 6789);
        out = new DataOutputStream(socket.getOutputStream());
        in = new DataInputStream(socket.getInputStream());
    }

    /**
     * Метод получения файлов сервера
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private void getServerFileList() throws IOException, ClassNotFoundException {
        serverWindow.getChildren().removeIf((item) -> {
            return true;
        });
        out.write("list".getBytes(StandardCharsets.UTF_8));
        int countOfFiles = in.readInt();
        List<File> files = new ArrayList<>();

        for (int i = 0; i < countOfFiles; i++) {
            int countOfBytes = in.readInt();
            byte buffer[] = new byte[countOfBytes];
            in.read(buffer, 0, countOfBytes);
            ByteArrayInputStream bais = new ByteArrayInputStream(buffer);
            ObjectInputStream ois = new ObjectInputStream(bais);
            File file = (File) ois.readObject();
            files.add(file);
            if(file.isDirectory()){
                serverDirectorySizes.put(file, in.readLong());
            }
        }

        out.write("Files received".getBytes(StandardCharsets.UTF_8));

        File[] f = new File[files.size()];
        files.toArray(f);
        drawFiles(f, serverWindow, false);
    }

    /**
     * Метод получения файлов клиента
     */
    private void getClientFileList() {
        clientWindow.getChildren().removeIf((item) -> {
            return true;
        });
        drawFiles(clientFiles.listFiles(), clientWindow, true);
    }

    /**
     * Метод отрисовки файлов и папко
     * @param files массив файлов
     * @param window окно, в котором нужно отрисовать
     * @param client true - окно клиента, false - окно сервера
     */
    public void drawFiles(File[] files, VBox window, boolean client){

        if(files == null){
            return;
        }

        List<File> filesList = getFilesForDraw(files, client);

        for (File file : filesList) {
            final HBox fileBox = new HBox();
            ImageView icon;
            if (file.isFile() && !file.toString().equals("..")) {
                icon = new ImageView("/images/file.png");
            } else {
                icon = new ImageView("/images/folder.png");
            }

            icon.setFitWidth(15.0);
            icon.setFitHeight(15.0);

            addActionListener(fileBox, client, file);

            Text name = new Text(file.getName());
            long length = 0;
            try {
                length = getSize(file, client);
            } catch (IOException e) {
                e.printStackTrace();
            }
            Text size = new Text(file.getName().equals("..")?"":" "+String.format("%.2f",(double)(length/1024))+"kB");
            Text lastMod = new Text(file.getName().equals("..")?"":" "+new SimpleDateFormat("y-M-d H:m:s").format(new Date(file.lastModified())));
            lastMod.setStyle("-fx-font-size: 11px;");
            size.setStyle("-fx-font-size: 11px; -fx-pref-height: 15px;");
            name.setId(file.isFile() ? "file" : "folder");
            fileBox.getChildren().addAll(icon, name, size, lastMod);
            window.getChildren().add(fileBox);
        }
        ImageView iconFolderAdd = new ImageView("/images/add_folder.png");
        iconFolderAdd.setFitWidth(15.0);
        iconFolderAdd.setFitHeight(15.0);

        HBox addFolderHBox = new HBox();
        TextField folderNameField = new TextField();
        folderNameField.setStyle("-fx-padding: 0px; -fx-pref-height: 12px; -fx-font-size: 12px");

        iconFolderAdd.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                addDirectory(folderNameField.getText(), client);
            }
        });

        folderNameField.setOnAction(event -> {
            addDirectory(folderNameField.getText(), client);
        });

        addFolderHBox.getChildren().addAll(iconFolderAdd, folderNameField);
        window.getChildren().add(addFolderHBox);
    }

    /**
     * Отслеживание события клика по файлам и папкам
     * @param fileBox строка с названием файла
     * @param client true - на компьютере пользователя, false - на сервере
     * @param file файл
     */
    private void addActionListener(HBox fileBox, boolean client, File file){
        fileBox.addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent mouseEvent) {
                if (fileBox.getId() == null) {
                    HashSet<Node> nodes = new HashSet<>();
                    nodes.addAll(clientWindow.getChildren());
                    nodes.addAll(serverWindow.getChildren());
                    for (Node node : nodes) {
                        if (node.getId() != null && node.getId().equals("selectedFile")) {
                            node.setId(null);
                        }
                    }
                    if(!"..".equals(file.getName())) {
                        if (client) {
                            uploadBtn.setVisible(true);
                            uploadBtn.setManaged(true);
                            downloadBtn.setVisible(false);
                            downloadBtn.setManaged(false);
                            clientWindowFocused = true;
                        } else {
                            uploadBtn.setVisible(false);
                            uploadBtn.setManaged(false);
                            downloadBtn.setVisible(true);
                            downloadBtn.setManaged(true);
                            clientWindowFocused = false;
                        }
                        btnPanel.setVisible(true);
                    } else{
                        btnPanel.setVisible(false);
                    }
                    fileBox.setId("selectedFile");
                } else {
                    Text folder = (Text) fileBox.lookup("#folder");
                    if (folder != null) {
                        if (client) {
                            if (folder.getText().equals("..")) {
                                clientFiles = new File(clientFiles.getParent());
                            } else {
                                clientFiles = new File(clientFiles.toString() + File.separator + folder.getText() + File.separator);
                            }
                            getClientFileList();
                            btnPanel.setVisible(false);
                        } else {
                            try {
                                out.write(("open " + folder.getText()).getBytes(StandardCharsets.UTF_8));
                                byte[] response = new byte[100];
                                in.read(response);
                                System.out.println(new String(response));
                                getServerFileList();
                                btnPanel.setVisible(false);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    } else {
                        fileBox.setId(null);
                        btnPanel.setVisible(false);
                    }
                }
            }
        });
    }

    /**
     * Формирует список файлов для отрисовки согласно сортировке
     * @param files список файлов
     * @param client true - на компьютере пользователя, false - на сервере
     * @return отсортированный список файлов
     */
    private List<File> getFilesForDraw(File[] files, boolean client){
        List<File> filesList = new ArrayList<>();
        filesList.addAll(Arrays.asList(files));

        if (!clientFiles.toString().equals(userLogin) && client) {
            File backFolder = new File("..");
            filesList.add(backFolder);
        }

        Collections.sort(filesList, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                switch(String.valueOf(sortType.getValue())){
                    case "по возрастанию имени":{
                        return o1.getName().compareTo(o2.getName());
                    }
                    case "по возрастанию размера":{
                        try {
                            return (int)(getSize(o1, client) - getSize(o2, client));
                        } catch (IOException e) {
                            e.printStackTrace();
                            return 0;
                        }
                    }
                    case "по возрастанию даты изменения":{
                        return (int)(o1.lastModified()-o2.lastModified());
                    }
                    case "по убыванию имени":{
                        return o2.getName().compareTo(o1.getName());
                    }
                    case "по убыванию размера":{
                        try {
                            return (int)(getSize(o2, client) - getSize(o1, client));
                        } catch (IOException e) {
                            e.printStackTrace();
                            return 0;
                        }
                    }
                    case "по убыванию даты изменения":{
                        return (int)(o2.lastModified()-o1.lastModified());
                    }
                    default:{
                        return o1.getName().compareTo(o2.getName());
                    }
                }
            }});
        return filesList;
    }

    /**
     * Создание директории
     * @param folderName Название директории
     * @param client true - на компьютере пользователя, false - на сервере
     */
    private void addDirectory(String folderName, boolean client) {
        if (folderName.length() <= 0) {
            return;
        }
        if (client) {
            File folder = new File(clientFiles + File.separator + folderName);
            folder.mkdirs();
            getClientFileList();
        } else {
            try {
                out.write(("mkdir " + folderName).getBytes(StandardCharsets.UTF_8));
                getServerFileList();
            } catch (Exception e) {
                System.out.println("Couldn't create folder");
            }
        }
    }

    /**
     * Метод обработки нажатия на кнопку "Скачать"
     * @param actionEvent обрабатываемое событие
     * @throws IOException
     */
    public void getFile(ActionEvent actionEvent) throws IOException {
        String filename = "";
        String type = "";
        for (Node node : serverWindow.getChildren()) {
            if (node.getId() != null && node.getId().equals("selectedFile")) {
                filename = ((Text) ((HBox) node).getChildren().get(1)).getText();
                type = (((HBox) node).getChildren().get(1)).getId();
            }
        }
        File file = new File(clientFiles + File.separator + filename);
        out.write(("download " + file.getName()).getBytes(StandardCharsets.UTF_8));

        if(type.equals("folder")){
            while(true){
                byte[] responseArray = new byte[100];
                in.read(responseArray);
                String response = new String(responseArray).trim();
                if(response.startsWith("complete")){
                    break;
                } else if(response.startsWith("mkdir")){
                    String[] args = response.split(" ", 2);
                    File dir = new File(clientFiles + File.separator + args[1] + File.separator);
                    dir.mkdirs();
                    clientFiles = dir;
                    out.write("next".getBytes(StandardCharsets.UTF_8));
                } else if(response.startsWith("back")){
                    clientFiles = clientFiles.getParentFile();
                    out.write("next".getBytes(StandardCharsets.UTF_8));
                } else if(response.startsWith("file")){
                    String[] args = response.split(" ", 2);
                    String name = args[1];
                    File f = new File(clientFiles + File.separator + name);
                    out.write("next".getBytes(StandardCharsets.UTF_8));
                    download(f);
                    out.write("next".getBytes(StandardCharsets.UTF_8));
                }
            }
            getClientFileList();
        } else{
            download(file);
            getClientFileList();
        }
    }

    /**
     * Метод скачивания файла с сервера
     * @param file
     */
    private void download(File file){
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            long size = in.readLong();

            byte[] buffer = new byte[1024 * 8];
            FileOutputStream fos = new FileOutputStream(file);
            for (int i = 0; i < (size + (8 * 1024 - 1)) / (8 * 1024); i++) {
                int read = in.read(buffer);
                fos.write(buffer, 0, read);
            }
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Метод обработки нажатие на кнопку "Загрузить"
     * @param actionEvent обрабатываемое событие
     */
    public void sendFile(ActionEvent actionEvent) {
        String filename = "";
        for (Node node : clientWindow.getChildren()) {
            if (node.getId() != null && node.getId().equals("selectedFile")) {
                filename = ((Text) ((HBox) node).getChildren().get(1)).getText();
            }
        }

        try {
            File file = new File(clientFiles + File.separator + filename);

            if (!file.exists() && file.isFile()) {
                throw new FileNotFoundException();
            }

            if(file.isDirectory()){
                Files.walkFileTree(file.toPath(), new SimpleFileVisitor<Path>(){
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        uploadFile(file.toFile());
                        return super.visitFile(file, attrs);
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        String folder = dir.toFile().getName();
                        addDirectory(folder, false);
                        out.write(("open " + folder).getBytes(StandardCharsets.UTF_8));
                        byte[] response = new byte[100];
                        in.read(response);
                        return super.preVisitDirectory(dir, attrs);
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        out.write(("open ..").getBytes(StandardCharsets.UTF_8));
                        byte[] response = new byte[100];
                        in.read(response);
                        return super.postVisitDirectory(dir, exc);
                    }
                });
                getServerFileList();
            } else{
                uploadFile(file);
                getServerFileList();
            }
        } catch (FileNotFoundException e) {
            System.err.println("File not found - " + filename);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Метод загрузки файла на сервер
     * @param file передаваемый файл
     * @throws IOException
     */
    private void uploadFile(File file) throws IOException {
        String filename = file.getName();
        long fileLength = file.length();
        byte[] buffer = new byte[8 * 1024];
        FileInputStream fis = new FileInputStream(file);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        String message = "upload " + filename + " " + fileLength;
        out.write(message.getBytes(StandardCharsets.UTF_8));

        byte[] response = new byte[20];
        in.read(response);
        int read = 0;
        while ((read = fis.read(buffer)) != -1) {
            baos.write(buffer, 0, read);
        }
        out.write(baos.toByteArray());
        byte[] status = new byte[100];
        in.read(status);
        System.out.println("Sending status: " + new String(status));
    }

    /**
     * Метод переименования файла или папки
     * @param actionEvent обрабатываемое событие
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public void rename(ActionEvent actionEvent) throws IOException, ClassNotFoundException {
        String fileName = getSelectedFileName();
        String newFileName = renameField.getText();
        if ("".equals(newFileName)) {
            return;
        }

        if (clientWindowFocused) {
            File currentFile = new File(clientFiles + File.separator + fileName);
            File newFile = new File(clientFiles + File.separator + newFileName);
            if (currentFile.exists()) {
                currentFile.renameTo(newFile);
                getClientFileList();
            }
        } else {
            out.write(("rename " + fileName + " " + newFileName).getBytes(StandardCharsets.UTF_8));
            byte[] status = new byte[100];
            in.read(status);
            System.out.println("Status: " + new String(status));
            getServerFileList();
        }
        renameField.clear();
    }

    /**
     * Метод удаления файла или папки, срабатываемый при нажатии на кнопку удаления
     * @param actionEvent обрабабатываемое событие
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public void removeFile(ActionEvent actionEvent) throws IOException, ClassNotFoundException {
        String fileName = getSelectedFileName();

        if (clientWindowFocused) {
            File file = new File(clientFiles + File.separator + fileName);
            if (file.exists()) {
                if (file.isDirectory()) {
                    Path filePath = file.toPath();
                    Files.walkFileTree(filePath, new FileVisitor<Path>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            Files.delete(file);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                            Files.delete(dir);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } else {
                    file.delete();
                }
                getClientFileList();
            }
        } else {
            out.write(("delete " + fileName).getBytes(StandardCharsets.UTF_8));
            byte[] status = new byte[100];
            in.read(status);
            System.out.println("Status: " + new String(status));
            getServerFileList();
        }
    }

    /**
     * Метод определяет файл, выбранный пользователем
     * @return название выбранного файла
     */
    private String getSelectedFileName() {
        String fileName = "";
        VBox window = clientWindowFocused ? clientWindow : serverWindow;
        for (Node node : window.getChildren()) {
            if (node.getId() != null && node.getId().equals("selectedFile")) {
                fileName = ((Text) ((HBox) node).getChildren().get(1)).getText();
            }
        }
        return fileName;
    }

    /**
     * Метод регистрации нового пользователя в системе
     * @param login логин нового пользователя
     * @param password пароль нового пользователя
     * @return сообщение о результате выполнения операции регистрации
     */
    public String registration(String login, String password){
        try {
            out.write(("registration "+ login+" "+password).getBytes(StandardCharsets.UTF_8));
            byte[] responseArray = new byte[150];
            in.read(responseArray);
            return new String(responseArray, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            return "Не удалось отправить запрос. Повторите еще раз";
        }
    }

    /**
     * Инициализация окна регистрации пользователя
     */
    public void initRegWindow() {

        try{
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getClassLoader().getResource("registration.fxml"));
            Parent root = fxmlLoader.load();
            regController = fxmlLoader.getController();
            regController.setMainController(this);

            regStage = new Stage();
            regStage.setTitle("Файловый менеджер: регистрация");
            regStage.getIcons().add(new Image("images/icon.jpg"));
            regStage.setScene(new Scene(root, 450, 350));
            regStage.initStyle(StageStyle.UTILITY);
            regStage.initModality(Modality.APPLICATION_MODAL);

            if(regStage == null){
                initRegWindow();
            }
            regStage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Обработка нажатия кнопки авторизации
     * @param actionEvent обрабатываемое событие
     */
    public void login(ActionEvent actionEvent) {
        String login = loginField.getText();
        String password = passwordField.getText();

        try {
            out.write(("login "+login+" "+password).getBytes(StandardCharsets.UTF_8));
            byte[] response = new byte[100];
            in.read(response);
            String msg = new String(response, StandardCharsets.UTF_8);
            if(msg.startsWith("Success")){
                clientFiles = new File(login);
                userLogin = login;
                if (!clientFiles.exists()) {
                    clientFiles.mkdir();
                }
                mainWindow.setVisible(true);
                loginWindow.setVisible(false);
                getClientFileList();
                getServerFileList();
            } else{
                message.setText("Не правильно указан логин/пароль");
            }
        } catch (Exception e) {
            e.printStackTrace();
            message.setText("Ошибка отправки данных. Попробуйте еще раз");
        }
    }

    public void regOpen(ActionEvent actionEvent) {
        initRegWindow();
    }

    /**
     * Метод обработки события изменения типа сортировки
     * @param actionEvent обрабатываемое событие
     */
    public void changeSort(ActionEvent actionEvent) {
        getClientFileList();
        try {
            getServerFileList();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }


    /**
     * Метод получени размера файла
     * @param file файл, размер которого необходимо получить
     * @param client true - файл на стороне клиента, false - на стороне сервера
     * @return размер файла в байтах
     * @throws IOException
     */
    private long getSize(File file, boolean client) throws IOException {
        if(client && !file.exists()){
            return 0;
        }

        if(file.isFile()){
            return file.length();
        } else{

            if(!client){
                return serverDirectorySizes.get(file);
            }

            AtomicLong size = new AtomicLong(0);
            Files.walkFileTree(file.toPath(), new SimpleFileVisitor<Path>(){
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    size.addAndGet(attrs.size());
                    return super.visitFile(file, attrs);
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    System.out.println("Не удалось получить размер файла "+file.getFileName());
                    return super.visitFileFailed(file, exc);
                }
            });
            return size.get();
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

    }
}
