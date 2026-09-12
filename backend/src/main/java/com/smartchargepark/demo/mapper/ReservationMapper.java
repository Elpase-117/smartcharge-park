package com.smartchargepark.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartchargepark.demo.model.Reservation;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

public interface ReservationMapper extends BaseMapper<Reservation> {
    @Select("""
            SELECT COUNT(*) FROM reservation
            WHERE station_id = #{stationId}
              AND reservation_date = #{reservationDate}
              AND time_slot = #{timeSlot}
              AND status IN ('PENDING_PAYMENT', 'CONFIRMED')
            """)
    long countActive(@Param("stationId") Long stationId,
                     @Param("reservationDate") LocalDate reservationDate,
                     @Param("timeSlot") String timeSlot);
}

