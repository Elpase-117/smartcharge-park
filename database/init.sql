CREATE DATABASE IF NOT EXISTS smartcharge_demo
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE smartcharge_demo;

DROP TABLE IF EXISTS demo_order;
DROP TABLE IF EXISTS reservation;
DROP TABLE IF EXISTS station;
DROP TABLE IF EXISTS app_user;

CREATE TABLE app_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    display_name VARCHAR(50) NOT NULL,
    role VARCHAR(30) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE station (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL,
    address VARCHAR(255) NOT NULL,
    operator_brand VARCHAR(100),
    operating_hours VARCHAR(100),
    station_type VARCHAR(30) NOT NULL,
    available_parking INT,
    available_chargers INT NOT NULL,
    total_chargers INT NOT NULL,
    slot_capacity INT NOT NULL,
    parking_fee DECIMAL(10, 2) NOT NULL,
    connector_type VARCHAR(50),
    charging_mode VARCHAR(30) NOT NULL,
    rated_power_kw DECIMAL(10, 2),
    power_summary VARCHAR(80) NOT NULL,
    voltage_v INT,
    electricity_price DECIMAL(10, 2) NOT NULL,
    price_detail VARCHAR(255),
    service_fee DECIMAL(10, 2) NOT NULL,
    compatibility VARCHAR(200),
    parking_policy VARCHAR(255),
    data_source VARCHAR(100) NOT NULL,
    snapshot_time VARCHAR(100) NOT NULL,
    data_notice VARCHAR(255) NOT NULL,
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
    name, address, operator_brand, operating_hours, station_type,
    available_parking, available_chargers, total_chargers, slot_capacity,
    parking_fee, connector_type, charging_mode, rated_power_kw, power_summary,
    voltage_v, electricity_price, price_detail, service_fee, compatibility,
    parking_policy, data_source, snapshot_time, data_notice, status
) VALUES
(
    '逸安启超级充电站（重庆北碚吾悦广场）',
    '北碚区天生街道梨园村60号地下LG层A区停车场',
    '逸安启', '周一至周日 00:00-24:00', 'CHARGING_SITE',
    NULL, 4, 4, 2,
    0.00, '暂无公开数据', '超充', 600.00, '600.0 kW',
    1000, 1.31, '公开页面展示分时价格趋势；采集时点当前价 ¥1.31/度',
    0.00, '暂无公开数据',
    '绑定车牌的充电车辆1小时内免费停车；后续规则在公开页面截图中未完整显示',
    '高德地图公开页面', '13:32（采集日期未显示）',
    '空闲数量为采集时点状态快照；当前 Demo 未接入第三方实时接口',
    'SNAPSHOT_AVAILABLE'
),
(
    '广汽能源汽车充电站（广汽能源重庆北碚朝阳公园超充站）',
    '北碚区团山一路43号',
    '广汽能源', '周一至周日 00:00-24:00', 'CHARGING_SITE',
    NULL, 4, 6, 2,
    0.00, '暂无公开数据', '超充', 480.00, '480.0 kW',
    1000, 1.72, '高德专享价；公开页面展示分时价格趋势；采集时点当前价 ¥1.72/度',
    0.00, '暂无公开数据',
    '停车收费政策以现场实际规则为准',
    '高德地图公开页面', '13:32（采集日期未显示）',
    '空闲数量为采集时点状态快照；当前 Demo 未接入第三方实时接口',
    'SNAPSHOT_AVAILABLE'
),
(
    '汽车充电站（易安桩白溪路充电站）',
    '北碚区龙凤桥街道万科金域华府洋房2栋',
    '易安桩', '周一至周日 00:00-24:00', 'CHARGING_SITE',
    NULL, 21, 22, 2,
    0.00, '暂无公开数据', '快充', NULL, '80.0-90.0 kW',
    750, 1.23, '公开页面展示分时价格趋势；采集时点当前价 ¥1.23/度',
    0.00, '暂无公开数据',
    '绑定正确车牌前2小时免停；后续规则在公开页面截图中未完整显示',
    '高德地图公开页面', '13:32（采集日期未显示）',
    '空闲数量为采集时点状态快照；当前 Demo 未接入第三方实时接口',
    'SNAPSHOT_AVAILABLE'
),
(
    '小鹏超级充电站（小鹏S4超快充重庆北碚吾悦广场站）',
    '北碚区天生街道梨园村60号吾悦广场（重庆北碚店）',
    '小鹏', '周一至周日 00:00-24:00', 'CHARGING_SITE',
    NULL, 0, 4, 2,
    0.00, '暂无公开数据', '超充/快充', NULL, '超充 360.0 kW；快充 250.0 kW',
    1000, 1.07, '高德专享价；公开页面可切换超充/快充价格；采集时点当前价 ¥1.07/度',
    0.00, '暂无公开数据',
    '公开页面显示限免2小时；占位费规则未完整显示',
    '高德地图公开页面', '13:32（采集日期未显示）',
    '空闲数量为采集时点状态快照；当前 Demo 未接入第三方实时接口',
    'SNAPSHOT_FULL'
),
(
    '广汽能源汽车充电站（广汽昊铂重庆北碚吾悦广场超充站）',
    '北碚区天生重庆北碚吾悦广场地下停车场B1层C232-C237车位',
    '广汽能源', '周一至周日 00:00-24:00', 'CHARGING_SITE',
    NULL, 3, 6, 2,
    0.00, '暂无公开数据', '快充', 360.00, '360.0 kW',
    1000, 1.01, '公开页面显示采集时点各时段价格一致；当前价 ¥1.01/度',
    0.00, '暂无公开数据',
    '商场免停半小时，超过后3元/小时',
    '高德地图公开页面', '13:33（采集日期未显示）',
    '空闲数量为采集时点状态快照；当前 Demo 未接入第三方实时接口',
    'SNAPSHOT_AVAILABLE'
),
(
    '新电途汽车充电站（鑫牛北碚区碚城江畔充电站）',
    '北碚区天生街道河嘉路180号碚城江畔',
    '新电途', '周一至周日 00:00-24:00', 'CHARGING_SITE',
    NULL, 14, 26, 2,
    0.00, '暂无公开数据', '慢充', NULL, '7.0-20.0 kW',
    1000, 0.83, '高德专享价；公开页面显示低谷时段 ¥0.78/度；采集时点当前价 ¥0.83/度',
    0.00, '暂无公开数据',
    '公开页面截图未完整显示明确停车政策',
    '高德地图公开页面', '13:33（采集日期未显示）',
    '空闲数量为采集时点状态快照；当前 Demo 未接入第三方实时接口',
    'SNAPSHOT_AVAILABLE'
),
(
    '国家电网汽车充电站（重庆市北碚区缙善路充电站）',
    '北碚区缙善路缙能花园地面停车场',
    '国家电网', '24小时营业', 'CHARGING_SITE',
    NULL, 4, 7, 2,
    0.00, '暂无公开数据', '快充', 120.00, '120.0 kW',
    750, 1.09, '公开页面显示采集时点各时段价格一致；当前价 ¥1.09/度',
    0.00, '暂无公开数据',
    '停车免费',
    '高德地图公开页面', '13:34（采集日期未显示）',
    '空闲数量为采集时点状态快照；当前 Demo 未接入第三方实时接口',
    'SNAPSHOT_AVAILABLE'
),
(
    '特来电超级充电站（重庆燃气文星湾充电站）',
    '北碚区丽锦雅舍南1门东50米',
    '特来电', '周一至周日 00:00-24:00', 'CHARGING_SITE',
    NULL, 12, 15, 2,
    0.00, '暂无公开数据', '超充/快充', NULL, '超充 480.0 kW；快充 120.0-180.0 kW',
    1000, 1.35, '公开页面展示分时价格趋势；采集时点当前价 ¥1.35/度',
    0.00, '暂无公开数据',
    '公开页面显示充电车辆有停车优惠/免费信息及占位费说明，完整规则未显示',
    '高德地图公开页面', '13:34（采集日期未显示）',
    '空闲数量为采集时点状态快照；当前 Demo 未接入第三方实时接口',
    'SNAPSHOT_AVAILABLE'
),
(
    '国家电网汽车充电站（重庆市北碚区五农街停车场充电站）',
    '北碚区天生路地面停车场01（西南大学地铁站1号口步行260米）',
    '国家电网', '24小时营业', 'CHARGING_SITE',
    NULL, 3, 4, 2,
    0.00, '暂无公开数据', '快充', 80.00, '80.0 kW',
    750, 1.17, '公开页面展示价格趋势；采集时点当前价 ¥1.17/度',
    0.00, '暂无公开数据',
    '停车费无',
    '高德地图公开页面', '13:34（采集日期未显示）',
    '空闲数量为采集时点状态快照；当前 Demo 未接入第三方实时接口',
    'SNAPSHOT_AVAILABLE'
),
(
    '中国石化西大充电站（石化易电-中国石化西南大学超充站）',
    '北碚区天生路与融汇南路交叉口西侧（西南大学西南侧）',
    '石化易电/中国石化', '周一至周日 00:00-24:00', 'CHARGING_SITE',
    NULL, 18, 20, 2,
    0.00, '暂无公开数据', '超充', NULL, '250.0-600.0 kW',
    1000, 1.27, '公开页面显示原价 ¥1.37/度并减 ¥0.10/度；展示分时价格趋势；采集时点当前价 ¥1.27/度',
    0.00, '暂无公开数据',
    '充电免费停车',
    '高德地图公开页面', '13:34（采集日期未显示）',
    '空闲数量为采集时点状态快照；当前 Demo 未接入第三方实时接口',
    'SNAPSHOT_AVAILABLE'
);

