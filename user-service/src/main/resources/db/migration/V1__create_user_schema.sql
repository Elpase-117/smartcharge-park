CREATE TABLE app_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    display_name VARCHAR(50) NOT NULL,
    role VARCHAR(30) NOT NULL,
    access_token VARCHAR(64) UNIQUE
) ENGINE=InnoDB;

-- Keep the original Demo identity so existing user_id=1 records retain meaning.
INSERT INTO app_user (id, username, password, display_name, role)
VALUES (1, 'driver', '123456', '演示车主', 'DRIVER'),
       (2, 'driver2', '123456', '演示车主二', 'DRIVER');
