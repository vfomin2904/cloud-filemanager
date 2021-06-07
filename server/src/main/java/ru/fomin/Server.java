package ru.fomin;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class Server {

    private final int BUFFER_SIZE = 8 * 1024;
    private final ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
    private HashMap<SocketAddress, File> clientPaths = new HashMap<>();
    private HashMap<SocketAddress, String> clientLogin = new HashMap<>();
    private Repository repository;

    public Server() throws IOException {
        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress(6789));
        server.configureBlocking(false);
        Selector selector = Selector.open();
        server.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("Server started");

        repository = Repository.getRepository();

        while (server.isOpen()) {
            selector.select();

            Set<SelectionKey> selectionKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeys.iterator();

            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                if (key.isAcceptable()) {
                    handleAccept(key, selector);
                } else if (key.isReadable()) {
                    handleRead(key, selector);
                }
                iterator.remove();
            }
        }
    }

    /**
     * Метод обработки соединения нового пользователя
     * @param key ключ события
     * @param selector селектор
     * @throws IOException
     */
    private void handleAccept(SelectionKey key, Selector selector) throws IOException {
        SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
        channel.configureBlocking(false);
        System.out.println("Client accepted. IP: " + channel.getRemoteAddress());
        channel.register(selector, SelectionKey.OP_READ);
    }


    /**
     * Метод обработки сообщений пользователей
     * @param key ключ события
     * @param selector селектор
     * @throws IOException
     */
    private void handleRead(SelectionKey key, Selector selector) throws IOException {
        buffer.clear();
        SocketChannel channel = ((SocketChannel) key.channel());

        try {
            StringBuilder sb = new StringBuilder();
            while (channel.read(buffer) > 0) {
                buffer.flip();
                while (buffer.hasRemaining()) {
                    sb.append((char) buffer.get());
                }
                buffer.clear();
            }

            buffer.clear();
            String command = sb.toString();

            if (command.length() == 0) {
                return;
            }

            if (command.startsWith("upload")) {
                String[] args = command.split(" ", 3);
                uploading(channel, args[1], Integer.parseInt(args[2]));
            } else if (command.startsWith("download")) {
                String[] args = command.split(" ", 2);
                downloading(channel, args[1]);
            } else if ("list".equals(command)) {
                getListFiles(channel);
            } else if (command.startsWith("delete")) {
                String[] args = command.split(" ", 2);
                removeFile(channel, args[1]);
            } else if (command.startsWith("rename")) {
                String[] args = command.split(" ", 3);
                renameFile(channel, args[1], args[2]);
            } else if (command.startsWith("mkdir")) {
                String[] args = command.split(" ", 2);
                mkdir(channel, args[1]);
            } else if (command.startsWith("open ")) {
                String[] args = command.split(" ", 2);
                openDirectory(channel, args[1]);
            } else if (command.startsWith("login ")) {
                String[] args = command.split(" ", 3);
                authorize(channel, args[1], args[2]);
            } else if (command.startsWith("registration")) {
                String[] args = command.split(" ", 3);
                registration(channel, args[1], args[2]);
            } else if ("exit".equals(command)) {
                disconnected(channel);
            }
        } catch(SocketException e){
            disconnected(channel);
        }
    }

    /**
     * Обработка запроса на регистрацию нового пользователя
     * @param channel канал пользователя
     * @param login логин
     * @param password пароль
     * @throws IOException
     */
    private void registration(SocketChannel channel, String login, String password) throws IOException {
        try {
            if (repository.addUser(login, password)) {
                channel.write(ByteBuffer.wrap("Пользователь успешно зарегистрирован".getBytes(StandardCharsets.UTF_8)));
            } else {
                channel.write(ByteBuffer.wrap("Пользователь с таким именем уже существует".getBytes(StandardCharsets.UTF_8)));
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
            channel.write(ByteBuffer.wrap("Потеряно соединение с базой данных. Попробуйте еще раз".getBytes(StandardCharsets.UTF_8)));
        }
    }

    /**
     * Обработка запроса на авторизацию пользователя
     * @param channel канал пользователя
     * @param login логин
     * @param password пароль
     * @throws IOException
     */
    private void authorize(SocketChannel channel, String login, String password) throws IOException {
        try {
            if (repository.findUser(login, password)) {
                clientLogin.put(channel.getRemoteAddress(), login);
                channel.write(ByteBuffer.wrap("Success".getBytes(StandardCharsets.UTF_8)));
                File userDir = new File("server_" + login + File.separator);
                if (!userDir.exists()) {
                    userDir.mkdir();
                }
                clientPaths.put(channel.getRemoteAddress(), userDir);
            } else {
                channel.write(ByteBuffer.wrap("Auth error. User not found".getBytes(StandardCharsets.UTF_8)));
            }
        } catch (Exception throwables) {
            throwables.printStackTrace();
            channel.write(ByteBuffer.wrap("Auth error. Failed to connect to the database".getBytes(StandardCharsets.UTF_8)));
        }
    }

    /**
     * Метод открытия папки на сервере
     * @param channel канал пользователя
     * @param folderName название папки
     * @throws IOException
     */
    private void openDirectory(SocketChannel channel, String folderName) throws IOException {
        if (folderName.equals("..")) {
            clientPaths.put(channel.getRemoteAddress(), new File(clientPaths.get(channel.getRemoteAddress()).getParent()));
        } else {
            clientPaths.put(channel.getRemoteAddress(), new File(clientPaths.get(channel.getRemoteAddress()) + File.separator + folderName));
        }
        channel.write(ByteBuffer.wrap("The folder is open".getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Метод создания папки на сервере
     * @param channel канал пользователя
     * @param folderName название папки
     */
    private void mkdir(SocketChannel channel, String folderName) {
        File folder = null;
        try {
            folder = new File(clientPaths.get(channel.getRemoteAddress()) + File.separator + folderName);
            folder.mkdirs();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Метод переименования файла или папки
     * @param channel канал пользователя
     * @param fileName старое название
     * @param newFileName новое название
     * @throws IOException
     */
    private void renameFile(SocketChannel channel, String fileName, String newFileName) throws IOException {
        File currentFile = new File(clientPaths.get(channel.getRemoteAddress()) + File.separator + fileName);
        File newFile = new File(clientPaths.get(channel.getRemoteAddress()) + File.separator + newFileName);
        if (currentFile.exists()) {
            currentFile.renameTo(newFile);
        }
        try {
            channel.write(ByteBuffer.wrap("File renamed successfully".getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Метод удаления папки или файла
     * @param channel канал пользователя
     * @param fileName название файла
     */
    private void removeFile(SocketChannel channel, String fileName) {
        File file = null;
        try {
            file = new File(clientPaths.get(channel.getRemoteAddress()) + File.separator + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
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
                channel.write(ByteBuffer.wrap("File removed successfully".getBytes(StandardCharsets.UTF_8)));
            } else {
                channel.write(ByteBuffer.wrap("File not found".getBytes(StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Метод обработки запроса на получения списка фалов и папок а сервере
     * @param channel канал пользователя
     * @throws IOException
     */
    private void getListFiles(SocketChannel channel) throws IOException {
        File currentDirectory = clientPaths.get(channel.getRemoteAddress());
        buffer.clear();
        File[] files = currentDirectory.listFiles();

        LinkedList<File> filesList = new LinkedList<>(Arrays.asList(files));

        if (!currentDirectory.toString().equals("server_" + clientLogin.get(channel.getRemoteAddress()))) {
            File backFolder = new File("..");
            filesList.addFirst(backFolder);
        }

        buffer.putInt(filesList.size());

        for (File file : filesList) {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(file);
            byte[] byteFile = byteArrayOutputStream.toByteArray();
            buffer.putInt(byteFile.length);
            buffer.put(byteFile);
            if (file.isDirectory()) {
                buffer.putLong(getSize(file));
            }
        }

        buffer.flip();
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
        buffer.clear();
    }

    /**
     * Метод получения размера папки или файла на сервере
     * @param file файл или папка
     * @return размер папки или файла в байтах
     * @throws IOException
     */
    private long getSize(File file) throws IOException {
        if (!file.exists()) {
            return 0;
        }

        if (file.isFile()) {
            return file.length();
        } else {
            AtomicLong size = new AtomicLong(0);
            Files.walkFileTree(file.toPath(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    size.addAndGet(attrs.size());
                    return super.visitFile(file, attrs);
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    System.out.println("Не удалось получить размер файла " + file.getFileName());
                    return super.visitFileFailed(file, exc);
                }
            });
            return size.get();
        }
    }

    /**
     * Мпетод скачивания файла или папки с сервера
     * @param channel канал пользователя
     * @param fileName название файла или папки
     * @throws IOException
     */
    private void downloading(SocketChannel channel, String fileName) throws IOException {
        File file = new File(clientPaths.get(channel.getRemoteAddress()) + File.separator + fileName);
        if (!file.exists() && file.isFile()) {
            return;
        }

        if (file.isDirectory()) {
            Files.walkFileTree(file.toPath(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    channel.write(ByteBuffer.wrap(("mkdir " + dir.toFile().getName()).getBytes(StandardCharsets.UTF_8)));
                    waitNext(channel);
                    return super.preVisitDirectory(dir, attrs);
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    channel.write(ByteBuffer.wrap(("file " + file.toFile().getName()).getBytes(StandardCharsets.UTF_8)));
                    waitNext(channel);
                    sendFile(channel, file.toFile());
                    waitNext(channel);
                    return super.visitFile(file, attrs);
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    channel.write(ByteBuffer.wrap("back".getBytes(StandardCharsets.UTF_8)));
                    waitNext(channel);
                    return super.postVisitDirectory(dir, exc);
                }
            });
            channel.write(ByteBuffer.wrap("complete".getBytes(StandardCharsets.UTF_8)));
        } else {
            sendFile(channel, file);
        }
    }

    /**
     * Метод ожидания готовности клиента к получению следующего сообщения
     * @param channel канал пользователя
     * @throws IOException
     */
    private void waitNext(SocketChannel channel) throws IOException {
        byte[] response = new byte[100];
        int offset = 0;
        while (true) {
            buffer.clear();
            channel.read(buffer);
            int length = buffer.position();
            buffer.flip();
            buffer.get(response, offset, length);
            if (new String(response).startsWith("next")) {
                break;
            }
            offset = length;
        }
        buffer.clear();
    }

    /**
     * Метод отправки файла клиенту
     * @param channel канал пользователя
     * @param file файл
     */
    private void sendFile(SocketChannel channel, File file) {
        try {
            long size = file.length();

            buffer.clear();
            buffer.putLong(size);
            buffer.flip();
            FileInputStream fis = new FileInputStream(file);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8 * 1024];
            int write = 0;
            while ((write = fis.read(buf)) != -1) {
                baos.write(buf, 0, write);
            }
            channel.write(buffer);
            buffer.clear();
            byte[] fileByte = baos.toByteArray();
            for (int i = 1; i <= fileByte.length; i++) {
                buffer.put(fileByte[i - 1]);
                if (i % BUFFER_SIZE == 0 || i == fileByte.length) {
                    buffer.flip();
                    channel.write(buffer);
                    buffer.clear();
                }
            }
            baos.close();
            fis.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Метод скачивания файла клиента на сервер
     * @param channel канал пользователя
     * @param name название файла
     * @param size размер файла
     * @throws IOException
     */
    private void uploading(SocketChannel channel, String name, int size) throws IOException {
        try {
            File file = new File(clientPaths.get(channel.getRemoteAddress()) + File.separator + name);
            if (!file.exists()) {
                file.createNewFile();
            }
            channel.write(ByteBuffer.wrap("Ready".getBytes(StandardCharsets.UTF_8)));

            buffer.clear();
            int i = 0;
            byte[] fileByte = new byte[size];
            while (i < size) {
                channel.read(buffer);
                int pos = buffer.position();
                if (pos == 0) {
                    continue;
                }
                buffer.flip();
                buffer.get(fileByte, i, pos);
                i += pos;
                buffer.clear();
            }

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(fileByte);
            fos.close();
            channel.write(ByteBuffer.wrap("File uploaded successfully".getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            System.out.println("Uploading error");
            e.printStackTrace();
        }
    }

    /**
     * Метод отключения пользователя от сервера
     * @param channel канал пользователя
     */
    private void disconnected(SocketChannel channel) {
        try {
            System.out.println("Client "+channel.getRemoteAddress()+" disconnected");
            channel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) throws IOException {
        new Server();
    }
}
