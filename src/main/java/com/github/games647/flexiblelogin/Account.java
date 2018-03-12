/*
 * This file is part of FlexibleLogin
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2018 contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.github.games647.flexiblelogin;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

import org.spongepowered.api.entity.living.player.Player;

public class Account {

    private static final String SQL_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private final UUID uuid;
    private final String username;

    private String passwordHash;
    private InetAddress ip;
    private String email;

    private boolean loggedIn;
    private Instant lastLogin;

    public Account(Player player, String password) {
        this(player.getUniqueId(), player.getName(), password, player.getConnection().getAddress().getAddress());
    }

    //new account
    public Account(UUID uuid, String username, String password, InetAddress ip) {
        this.uuid = uuid;
        this.username = username;
        this.passwordHash = password;

        this.ip = ip;
        this.lastLogin = Instant.now();
    }

    //existing account
    public Account(ResultSet resultSet, boolean sqlite) throws SQLException {
        //uuid in binary format
        ByteBuffer uuidBytes = ByteBuffer.wrap(resultSet.getBytes(2));

        this.uuid = new UUID(uuidBytes.getLong(), uuidBytes.getLong());
        this.username = resultSet.getString(3);
        this.passwordHash = resultSet.getString(4);

        try {
            byte[] bytes = resultSet.getBytes(5);
            if (bytes.length > 0) {
                this.ip = InetAddress.getByAddress(bytes);
            }
        } catch (UnknownHostException e) {
            this.ip = null;
        }

        this.lastLogin = parseTimestamp(resultSet, sqlite);
        this.email = resultSet.getString(7);
    }

    public synchronized boolean checkPassword(FlexibleLogin plugin, String userInput) throws Exception {
        return plugin.getHasher().checkPassword(passwordHash, userInput);
    }

    public UUID getUuid() {
        return uuid;
    }

    public synchronized String getUsername() {
        return username;
    }

    /* package */

    synchronized String getPassword() {
        return passwordHash;
    }
    public synchronized void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public synchronized InetAddress getIp() {
        return ip;
    }

    public synchronized void setIp(InetAddress ip) {
        this.ip = ip;
    }

    public synchronized Instant getLastLogin() {
        return lastLogin;
    }

    public synchronized Optional<String> getEmail() {
        if (email == null || email.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(email);
    }

    public synchronized void setEmail(String email) {
        this.email = email;
    }

    //these methods have to thread-safe as they will be accessed

    //through Async (PlayerChatEvent/LoginTask) and sync methods
    public synchronized boolean isLoggedIn() {
        return loggedIn;
    }
    public synchronized void setLoggedIn(boolean loggedIn) {
        if (loggedIn) {
            lastLogin = Instant.now();
        }

        this.loggedIn = loggedIn;
    }

    private Instant parseTimestamp(ResultSet resultSet, boolean sqlite) throws SQLException {
        if (sqlite) {
            //workaround for SQLite that causes time parsing errors in combination with CURRENT_TIMESTAMP in SQL
            String timestamp = resultSet.getString(6);
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(SQL_TIME_FORMAT);

            return LocalDateTime.parse(timestamp, timeFormatter).toInstant(ZoneOffset.UTC);
        }

        return resultSet.getTimestamp(6).toInstant();
    }

    @Override
    public synchronized String toString() {
        return this.getClass().getSimpleName() + '{' +
                "uuid=" + uuid +
                ", username='" + username + '\'' +
                ", ip=" + ip +
                ", email='" + email + '\'' +
                ", loggedIn=" + loggedIn +
                ", lastLogin=" + lastLogin +
                '}';
    }
}
