CREATE TABLE `demo_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_no` varchar(40) NOT NULL,
  `reservation_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `station_id` bigint NOT NULL,
  `status` varchar(30) NOT NULL,
  `estimated_amount` decimal(10,2) NOT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `order_no` (`order_no`),
  UNIQUE KEY `reservation_id` (`reservation_id`),
  KEY `fk_order_user` (`user_id`),
  KEY `fk_order_station` (`station_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

