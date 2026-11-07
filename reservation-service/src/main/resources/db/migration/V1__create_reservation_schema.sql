CREATE TABLE `reservation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `station_id` bigint NOT NULL,
  `reservation_date` date NOT NULL,
  `time_slot` varchar(30) NOT NULL,
  `status` varchar(30) NOT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_reservation_capacity` (`station_id`,`reservation_date`,`time_slot`,`status`),
  KEY `fk_reservation_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- A fixed mutex row serializes capacity check and insert across service instances.
-- It stores no capacity or remaining quantity.
CREATE TABLE reservation_write_guard (id BIGINT NOT NULL PRIMARY KEY) ENGINE=InnoDB;
INSERT INTO reservation_write_guard(id) VALUES (1);

