package com.smartchargepark.demo.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

@TableName("station")
public class Station {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String address;
    private String operatorBrand;
    private String operatingHours;
    private String stationType;
    private Integer availableParking;
    private Integer availableChargers;
    private Integer totalChargers;
    private Integer slotCapacity;
    private BigDecimal parkingFee;
    private String connectorType;
    private String chargingMode;
    private BigDecimal ratedPowerKw;
    private String powerSummary;
    private Integer voltageV;
    private BigDecimal electricityPrice;
    private String priceDetail;
    private BigDecimal serviceFee;
    private String compatibility;
    private String parkingPolicy;
    private String dataSource;
    private String snapshotTime;
    private String dataNotice;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getOperatorBrand() { return operatorBrand; }
    public void setOperatorBrand(String operatorBrand) { this.operatorBrand = operatorBrand; }
    public String getOperatingHours() { return operatingHours; }
    public void setOperatingHours(String operatingHours) { this.operatingHours = operatingHours; }
    public String getStationType() { return stationType; }
    public void setStationType(String stationType) { this.stationType = stationType; }
    public Integer getAvailableParking() { return availableParking; }
    public void setAvailableParking(Integer availableParking) { this.availableParking = availableParking; }
    public Integer getAvailableChargers() { return availableChargers; }
    public void setAvailableChargers(Integer availableChargers) { this.availableChargers = availableChargers; }
    public Integer getTotalChargers() { return totalChargers; }
    public void setTotalChargers(Integer totalChargers) { this.totalChargers = totalChargers; }
    public Integer getSlotCapacity() { return slotCapacity; }
    public void setSlotCapacity(Integer slotCapacity) { this.slotCapacity = slotCapacity; }
    public BigDecimal getParkingFee() { return parkingFee; }
    public void setParkingFee(BigDecimal parkingFee) { this.parkingFee = parkingFee; }
    public String getConnectorType() { return connectorType; }
    public void setConnectorType(String connectorType) { this.connectorType = connectorType; }
    public String getChargingMode() { return chargingMode; }
    public void setChargingMode(String chargingMode) { this.chargingMode = chargingMode; }
    public BigDecimal getRatedPowerKw() { return ratedPowerKw; }
    public void setRatedPowerKw(BigDecimal ratedPowerKw) { this.ratedPowerKw = ratedPowerKw; }
    public String getPowerSummary() { return powerSummary; }
    public void setPowerSummary(String powerSummary) { this.powerSummary = powerSummary; }
    public Integer getVoltageV() { return voltageV; }
    public void setVoltageV(Integer voltageV) { this.voltageV = voltageV; }
    public BigDecimal getElectricityPrice() { return electricityPrice; }
    public void setElectricityPrice(BigDecimal electricityPrice) { this.electricityPrice = electricityPrice; }
    public String getPriceDetail() { return priceDetail; }
    public void setPriceDetail(String priceDetail) { this.priceDetail = priceDetail; }
    public BigDecimal getServiceFee() { return serviceFee; }
    public void setServiceFee(BigDecimal serviceFee) { this.serviceFee = serviceFee; }
    public String getCompatibility() { return compatibility; }
    public void setCompatibility(String compatibility) { this.compatibility = compatibility; }
    public String getParkingPolicy() { return parkingPolicy; }
    public void setParkingPolicy(String parkingPolicy) { this.parkingPolicy = parkingPolicy; }
    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }
    public String getSnapshotTime() { return snapshotTime; }
    public void setSnapshotTime(String snapshotTime) { this.snapshotTime = snapshotTime; }
    public String getDataNotice() { return dataNotice; }
    public void setDataNotice(String dataNotice) { this.dataNotice = dataNotice; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

