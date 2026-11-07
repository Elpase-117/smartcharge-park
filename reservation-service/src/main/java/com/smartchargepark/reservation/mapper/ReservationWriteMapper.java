package com.smartchargepark.reservation.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartchargepark.reservation.model.Reservation;
import org.apache.ibatis.annotations.Select;
public interface ReservationWriteMapper extends BaseMapper<Reservation> {
    @Select("SELECT id FROM reservation_write_guard WHERE id = 1 FOR UPDATE")
    Long lockCreation();
}
