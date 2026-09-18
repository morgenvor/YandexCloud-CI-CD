package com.example;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@RestController
public class AppController {

    private final Connection dbConnection;

    public AppController(Connection dbConnection) {
        this.dbConnection = dbConnection;
    }

    @GetMapping("/get-data")
    public ResponseEntity getData() {
        List<User> users = fetchDataFromDB();
        return ResponseEntity.ok(users);
    }

    private List<User> fetchDataFromDB() {
        List<User> users = new ArrayList<>();
        try {
            String sqlStatement = "SELECT member_name, member_role FROM team_members";

            Statement stmt = dbConnection.createStatement();
            ResultSet rs = stmt.executeQuery(sqlStatement);
            while(rs.next()) {
                User user = new User();
                user.setName(rs.getString("member_name"));
                user.setRole(rs.getString("member_role"));
                users.add(user);
            }
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return users;
    }

    private static class User {
        String name;
        String role;

        private User() {

        }

        public User(String name, String role) {
            this.name = name;
            this.role = role;
        }

        private void setName(String name) {
            this.name = name;
        }

        private void setRole(String role) {
            this.role = role;
        }

        public String getName() {
            return name;
        }

        public String getRole() {
            return role;
        }
    }
}
