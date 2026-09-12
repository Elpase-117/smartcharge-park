package com.smartchargepark.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartchargepark.demo.model.Station;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface StationMapper extends BaseMapper<Station> {
    @Select("SELECT * FROM station WHERE id = #{id} FOR UPDATE")
    Station selectByIdForUpdate(@Param("id") Long id);
}
