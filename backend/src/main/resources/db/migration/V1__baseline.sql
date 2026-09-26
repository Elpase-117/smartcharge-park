CREATE TABLE app_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    display_name VARCHAR(50) NOT NULL,
    role VARCHAR(30) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE station (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    address VARCHAR(200) NOT NULL,
    station_type VARCHAR(30) NOT NULL,
    available_parking INT NOT NULL,
    available_chargers INT NOT NULL,
    slot_capacity INT NOT NULL,
    parking_fee DECIMAL(10, 2) NOT NULL,
    connector_type VARCHAR(50) NOT NULL,
    charging_mode VARCHAR(30) NOT NULL,
    rated_power_kw DECIMAL(10, 2) NOT NULL,
    electricity_price DECIMAL(10, 2) NOT NULL,
    service_fee DECIMAL(10, 2) NOT NULL,
    compatibility VARCHAR(200) NOT NULL,
    status VARCHAR(30) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE reservation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    station_id BIGINT NOT NULL,
    reservation_date DATE NOT NULL,
    time_slot VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_reservation_capacity (station_id, reservation_date, time_slot, status),
    CONSTRAINT fk_reservation_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_reservation_station FOREIGN KEY (station_id) REFERENCES station(id)
) ENGINE=InnoDB;

CREATE TABLE demo_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(40) NOT NULL UNIQUE,
    reservation_id BIGINT NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    station_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    estimated_amount DECIMAL(10, 2) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_order_reservation FOREIGN KEY (reservation_id) REFERENCES reservation(id),
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_order_station FOREIGN KEY (station_id) REFERENCES station(id)
) ENGINE=InnoDB;

INSERT INTO app_user (username, password, display_name, role)
VALUES ('driver', '123456', '演示车主', 'DRIVER');

INSERT INTO station (
    id, name, address, station_type, available_parking, available_chargers, slot_capacity,
    parking_fee, connector_type, charging_mode, rated_power_kw, electricity_price,
    service_fee, compatibility, status
) VALUES
(
    1, '科创园智慧停充站', '杭州市滨江区科创路 88 号', 'PARKING_CHARGING', 18, 6, 2,
    5.00, '国标直流接口', '快充', 120.00, 1.28, 0.35,
    '兼容符合国标直流充电接口的新能源汽车', 'AVAILABLE'
),
(
    2, '市民中心地下停充站', '杭州市上城区市民街 66 号', 'PARKING_CHARGING', 32, 10, 3,
    6.00, '国标交流接口', '慢充', 7.00, 0.98, 0.20,
    '兼容符合国标交流充电接口的新能源汽车', 'AVAILABLE'
);
