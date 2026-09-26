CREATE DATABASE IF NOT EXISTS smartcharge_demo
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE smartcharge_demo;

-- Flyway is the only source of truth for table structures and seed data.
-- Start the Spring Boot application after creating this empty database; Flyway
-- will baseline an existing legacy schema at V1 or create a new schema with V1,
-- then apply all later migrations without dropping reservation or order data.

