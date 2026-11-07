package com.smartchargepark.station.mapper;

import com.smartchargepark.station.model.Station;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface StationMapper {
    @Select("""
            <script>
            SELECT * FROM station
            <if test="keyword != null">
                WHERE name LIKE CONCAT('%', #{keyword}, '%')
                   OR operator_brand LIKE CONCAT('%', #{keyword}, '%')
                   OR address LIKE CONCAT('%', #{keyword}, '%')
            </if>
            ORDER BY id
            </script>
            """)
    List<Station> findAll(@Param("keyword") String keyword);

    @Select("SELECT * FROM station WHERE id = #{id}")
    Station findById(@Param("id") Long id);
}
