package org.example;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final String DB_URL = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) {
        try (Connection connection = DriverManager.getConnection(DB_URL)) {
            DatabaseInitializer.initialize(connection);

            MusicService musicService = new MusicService(connection);
            BookService bookService = new BookService(connection);
            UserService userService = new UserService(connection);

            // 1. Получить все музыкальные треки
            printSectionHeader("1. Все музыкальные композиции:");
            musicService.getAllMusic().forEach(System.out::println);

            // 2. Получить музыку без букв "м" и "т"
            printSectionHeader("\n2. Композиции без букв 'm' и 't':");
            musicService.getMusicWithoutLetters("mt").forEach(System.out::println);

            // 3. Добавить любимый трек
            printSectionHeader("\n3. Добавляем новую композицию...");
            musicService.addMusic(21, "Thunderstruck");
            System.out.println("Добавлена новая композиция: Thunderstruck");

            // 4. Обработать файл books.json
            printSectionHeader("\n4. Обрабатываем books.json...");
            processBooksJson(connection, bookService, userService);

            // 5. Книги отсортированы по годам
            printSectionHeader("\n5. Книги, отсортированные по году издания:");
            bookService.getBooksSortedByYear().forEach(System.out::println);

            // 6. Книги до 2000 года
            printSectionHeader("\n6. Книги, изданные до 2000 года:");
            bookService.getBooksBeforeYear(2000).forEach(System.out::println);

            // 7. Добавить личную информацию
            printSectionHeader("\n7. Добавляем информацию о себе...");
            addPersonalInfo(connection, userService, bookService);
            System.out.println("Информация добавлена:");
            userService.printUserBooks("Vladislav", "Potapov");

            // 8. Отбрасывание таблиц
            printSectionHeader("\n8. Удаляем таблицы...");
            DatabaseCleaner.dropTables(connection);
            System.out.println("Таблицы удалены.");

        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }
    }

    private static void printSectionHeader(String header) {
        System.out.println(header);
    }

    private static void processBooksJson(Connection connection, BookService bookService, UserService userService)
            throws SQLException, IOException {
        String json = Files.readString(Paths.get("src/main/java/org/example/books.json"));
        Type userListType = new TypeToken<List<User>>(){}.getType();
        List<User> users = GSON.fromJson(json, userListType);

        if (users == null) {
            System.err.println("Ошибка: не удалось загрузить данные из books.json");
            return;
        }

        for (User user : users) {
            if (!userService.exists(user)) {
                userService.add(user);
            }

            if (user.getFavoriteBooks() != null) {
                for (Book book : user.getFavoriteBooks()) {
                    if (!bookService.exists(book)) {
                        bookService.add(book);
                    }
                }
            }
        }
    }

    private static void addPersonalInfo(Connection connection, UserService userService, BookService bookService)
            throws SQLException {
        User me = new User("Vladislav", "Potapov", true, "8***3656070");
        if (!userService.exists(me)) {
            userService.add(me);
        }

        Book[] myBooks = {
                new Book("Martin Iden", "9780132350884", 2008, "Jack London", "Jack London"),
                new Book("Effective Java", "9780134685991", 2018, "Joshua Bloch", "Addison-Wesley")
        };

        for (Book book : myBooks) {
            if (!bookService.exists(book)) {
                bookService.add(book);
            }
        }
    }

    static class DatabaseInitializer {
        static void initialize(Connection connection) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS music(
                        id INT PRIMARY KEY,
                        name TEXT NOT NULL
                    )""");

                stmt.execute("""
                    INSERT INTO music (id, name)
                    SELECT * FROM (VALUES (1, 'Bohemian Rhapsody'),
                           (2, 'Stairway to Heaven'),
                           (3, 'Imagine'),
                           (4, 'Sweet Child O Mine'),
                           (5, 'Hey Jude'),
                           (6, 'Hotel California'),
                           (7, 'Billie Jean'),
                           (8, 'Wonderwall'),
                           (9, 'Smells Like Teen Spirit'),
                           (10, 'Let It Be'),
                           (11, 'I Want It All'),
                           (12, 'November Rain'),
                           (13, 'Losing My Religion'),
                           (14, 'One'),
                           (15, 'With or Without You'),
                           (16, 'Sweet Caroline'),
                           (17, 'Yesterday'),
                           (18, 'Dont Stop Believin'),
                           (19, 'Crazy Train'),
                           (20, 'Always')) AS new_data
                    WHERE NOT EXISTS (SELECT 1 FROM music)""");

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS users(
                        id INT PRIMARY KEY AUTO_INCREMENT,
                        name VARCHAR(50) NOT NULL,
                        surname VARCHAR(100) NOT NULL,
                        subscribed BOOLEAN NOT NULL DEFAULT FALSE,
                        phone VARCHAR(15) NOT NULL
                    )""");

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS books(
                        id INT PRIMARY KEY AUTO_INCREMENT,
                        name VARCHAR(100),
                        isbn VARCHAR(100),
                        publishing_year INT,
                        author VARCHAR(100),
                        publisher VARCHAR(100)
                    )""");
            }
        }
    }

    static class DatabaseCleaner {
        static void dropTables(Connection connection) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS users");
                stmt.execute("DROP TABLE IF EXISTS books");
                stmt.execute("DROP TABLE IF EXISTS music");
            }
        }
    }

    static class MusicService {
        private final Connection connection;

        MusicService(Connection connection) {
            this.connection = connection;
        }

        List<String> getAllMusic() throws SQLException {
            List<String> music = new ArrayList<>();
            String sql = "SELECT name FROM music";

            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    music.add(rs.getString("name"));
                }
            }
            return music;
        }

        List<String> getMusicWithoutLetters(String letters) throws SQLException {
            List<String> music = new ArrayList<>();
            String sql = "SELECT name FROM music WHERE LOWER(name) NOT LIKE ? AND LOWER(name) NOT LIKE ?";

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, "%" + letters.charAt(0) + "%");
                pstmt.setString(2, "%" + letters.charAt(1) + "%");

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        music.add(rs.getString("name"));
                    }
                }
            }
            return music;
        }

        void addMusic(int id, String name) throws SQLException {
            String sql = "INSERT INTO music (id, name) VALUES (?, ?)";

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setInt(1, id);
                pstmt.setString(2, name);
                pstmt.executeUpdate();
            }
        }
    }

    static class BookService {
        private final Connection connection;

        BookService(Connection connection) {
            this.connection = connection;
        }

        boolean exists(Book book) throws SQLException {
            String sql = "SELECT 1 FROM books WHERE isbn = ?";

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, book.getIsbn());
                try (ResultSet rs = pstmt.executeQuery()) {
                    return rs.next();
                }
            }
        }

        void add(Book book) throws SQLException {
            String sql = """
                INSERT INTO books (name, isbn, publishing_year, author, publisher) 
                VALUES (?, ?, ?, ?, ?)""";

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, book.getName());
                pstmt.setString(2, book.getIsbn());
                pstmt.setInt(3, book.getPublishingYear());
                pstmt.setString(4, book.getAuthor());
                pstmt.setString(5, book.getPublisher());
                pstmt.executeUpdate();
            }
        }

        List<Book> getBooksSortedByYear() throws SQLException {
            return getBooks("SELECT * FROM books ORDER BY publishing_year");
        }

        List<Book> getBooksBeforeYear(int year) throws SQLException {
            String sql = "SELECT * FROM books WHERE publishing_year < ? ORDER BY publishing_year";
            List<Book> books = new ArrayList<>();

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setInt(1, year);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        books.add(createBookFromResultSet(rs));
                    }
                }
            }
            return books;
        }

        private List<Book> getBooks(String sql) throws SQLException {
            List<Book> books = new ArrayList<>();

            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    books.add(createBookFromResultSet(rs));
                }
            }
            return books;
        }

        private Book createBookFromResultSet(ResultSet rs) throws SQLException {
            return new Book(
                    rs.getString("name"),
                    rs.getString("isbn"),
                    rs.getInt("publishing_year"),
                    rs.getString("author"),
                    rs.getString("publisher")
            );
        }
    }

    static class UserService {
        private final Connection connection;

        UserService(Connection connection) {
            this.connection = connection;
        }

        boolean exists(User user) throws SQLException {
            String sql = "SELECT 1 FROM users WHERE name = ? AND surname = ? AND phone = ?";

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, user.getName());
                pstmt.setString(2, user.getSurname());
                pstmt.setString(3, user.getPhone());

                try (ResultSet rs = pstmt.executeQuery()) {
                    return rs.next();
                }
            }
        }

        void add(User user) throws SQLException {
            String sql = """
                INSERT INTO users (name, surname, subscribed, phone) 
                VALUES (?, ?, ?, ?)""";

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, user.getName());
                pstmt.setString(2, user.getSurname());
                pstmt.setBoolean(3, user.isSubscribed());
                pstmt.setString(4, user.getPhone());
                pstmt.executeUpdate();
            }
        }

        void printUserBooks(String name, String surname) throws SQLException {
            String userSql = "SELECT * FROM users WHERE name = ? AND surname = ?";
            String booksSql = """
                SELECT b.* FROM books b
                JOIN users u ON 1=1
                WHERE u.name = ? AND u.surname = ?
                ORDER BY b.publishing_year""";

            try (PreparedStatement userStmt = connection.prepareStatement(userSql)) {
                userStmt.setString(1, name);
                userStmt.setString(2, surname);

                try (ResultSet userRs = userStmt.executeQuery()) {
                    if (userRs.next()) {
                        User user = new User(
                                userRs.getString("name"),
                                userRs.getString("surname"),
                                userRs.getBoolean("subscribed"),
                                userRs.getString("phone")
                        );
                        System.out.println("Пользователь: " + user);

                        try (PreparedStatement booksStmt = connection.prepareStatement(booksSql)) {
                            booksStmt.setString(1, name);
                            booksStmt.setString(2, surname);

                            try (ResultSet booksRs = booksStmt.executeQuery()) {
                                System.out.println("Любимые книги:");
                                while (booksRs.next()) {
                                    Book book = new Book(
                                            booksRs.getString("name"),
                                            booksRs.getString("isbn"),
                                            booksRs.getInt("publishing_year"),
                                            booksRs.getString("author"),
                                            booksRs.getString("publisher")
                                    );
                                    System.out.println("  " + book);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    static class User {
        private final String name;
        private final String surname;
        private final boolean subscribed;
        private final String phone;
        private List<Book> favoriteBooks;

        public User(String name, String surname, boolean subscribed, String phone) {
            this.name = name;
            this.surname = surname;
            this.subscribed = subscribed;
            this.phone = phone;
        }

        public String getName() { return name; }
        public String getSurname() { return surname; }
        public boolean isSubscribed() { return subscribed; }
        public String getPhone() { return phone; }
        public List<Book> getFavoriteBooks() { return favoriteBooks; }

        @Override
        public String toString() {
            return String.format("%s %s, телефон: %s, подписка: %s",
                    name, surname, phone, subscribed ? "активна" : "неактивна");
        }
    }

    static class Book {
        private final String name;
        private final String isbn;
        private final int publishingYear;
        private final String author;
        private final String publisher;

        public Book(String name, String isbn, int publishingYear, String author, String publisher) {
            this.name = name;
            this.isbn = isbn;
            this.publishingYear = publishingYear;
            this.author = author;
            this.publisher = publisher;
        }

        public String getName() { return name; }
        public String getIsbn() { return isbn; }
        public int getPublishingYear() { return publishingYear; }
        public String getAuthor() { return author; }
        public String getPublisher() { return publisher; }

        @Override
        public String toString() {
            return String.format("%s (%s), автор: %s, год: %d, издатель: %s",
                    name, isbn, author, publishingYear, publisher);
        }
    }
}