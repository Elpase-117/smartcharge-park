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
    private String stationType;
    private Integer availableParking;
    private Integer availableChargers;
    private Integer slotCapacity;
    private BigDecimal parkingFee;
    private String connectorType;
    private String chargingMode;
    private BigDecimal ratedPowerKw;
    private BigDecimal electricityPrice;
    private BigDecimal serviceFee;
    private String compatibility;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getStationType() { return stationType; }
    public void setStationType(String stationType) { this.stationType = stationType; }
    public Integer getAvailableParking() { return availableParking; }
    public void setAvailableParking(Integer availableParking) { this.availableParking = availableParking; }
    public Integer getAvailableChargers() { return availableChargers; }
    public void setAvailableChargers(Integer availableChargers) { this.availableChargers = availableChargers; }
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
    public BigDecimal getElectricityPrice() { return electricityPrice; }
    public void setElectricityPrice(BigDecimal electricityPrice) { this.electricityPrice = electricityPrice; }
    public BigDecimal getServiceFee() { return serviceFee; }
    public void setServiceFee(BigDecimal serviceFee) { this.serviceFee = serviceFee; }
    public String getCompatibility() { return compatibility; }
    public void setCompatibility(String compatibility) { this.compatibility = compatibility; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

