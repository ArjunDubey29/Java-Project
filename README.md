# Java-Project
This repo belongs to University.

<h1 align="center">🛒 Shopping Cart App — Java + MySQL</h1>

<p align="center">
  A simple <b>console-based shopping cart system</b> built using <b>Java</b>, <b>JDBC</b>, and <b>MySQL</b>.<br>
  Manage users, items, and orders — all from your terminal, powered by JDBC and clean SQL.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Language-Java-red?style=for-the-badge&logo=openjdk" alt="Java Badge">
  <img src="https://img.shields.io/badge/Database-MySQL-blue?style=for-the-badge&logo=mysql" alt="MySQL Badge">
  <img src="https://img.shields.io/badge/IDE-IntelliJ%20IDEA-purple?style=for-the-badge&logo=intellij-idea" alt="IntelliJ Badge">
  <img src="https://img.shields.io/badge/Build-Maven-orange?style=for-the-badge&logo=apache-maven" alt="Maven Badge">
</p>

---

## ⚙️ Overview

This project demonstrates **core JDBC operations** — connection handling, CRUD operations, and user-interaction through console menus.  
Users can log in, view items, place orders, and check their cart.  
Perfect for beginners learning **Java + MySQL integration**.

---

## 🧾 Features

- 🔐 **User Login System**
- 🧺 **View Items & Stock**
- 🛒 **Place Orders**
- 📦 **View Cart / Orders**
- 🧮 **Auto Stock Update** after purchase
- 🚪 **Logout / Exit** anytime

---

## 🧰 Tech Stack

| Component | Technology Used |
|------------|------------------|
| **Language** | Java (JDK 17+) |
| **Database** | MySQL 8.x |
| **Connector** | MySQL Connector/J (JDBC) |
| **IDE** | IntelliJ IDEA |
| **Build Tool** | Maven *(recommended)* |

---

## 🧠 Prerequisites

Make sure you have:

- ☕ **Java JDK 17+**
- 🐬 **MySQL Server 8.x**
- 💡 **IntelliJ IDEA**
- 🔗 **MySQL Connector/J** (via Maven or JAR)
- ✅ Basic SQL knowledge & command-line comfort

---

## 🧩 Database Setup (MySQL)

Run this SQL script before starting your app:

```sql
CREATE DATABASE IF NOT EXISTS shopping_cart_db CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE shopping_cart_db;

CREATE TABLE IF NOT EXISTS users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(100) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  full_name VARCHAR(200),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS items (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(200) NOT NULL,
  description TEXT,
  price DECIMAL(10,2) NOT NULL,
  stock INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS orders (
  id INT AUTO_INCREMENT PRIMARY KEY,
  user_id INT NOT NULL,
  item_id INT NOT NULL,
  quantity INT NOT NULL,
  order_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE
);

-- Sample Data
INSERT INTO users (username, password, full_name)
VALUES ('admin', 'admin123', 'Site Admin');

INSERT INTO items (name, description, price, stock) VALUES
('Razor', 'Sharp blade for close shave', 49.99, 10),
('Cloak', 'Weatherproof winter cloak', 99.50, 5),
('Potion', 'Heals wounds instantly', 9.99, 25);
