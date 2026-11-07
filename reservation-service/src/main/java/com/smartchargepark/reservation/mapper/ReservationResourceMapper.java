package com.smartchargepark.reservation.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

public interface ReservationResourceMapper {
    @Select("""
            SELECT COUNT(*) FROM reservation
            WHERE station_id = #{stationId}
              AND reservation_date = #{date}
              AND time_slot = #{timeSlot}
              AND status IN ('PENDING_PAYMENT', 'CONFIRMED')
            """)
    long countActive(@Param("stationId") Long stationId,
                     @Param("date") LocalDate date,
                     @Param("timeSlot") String timeSlot);
}
