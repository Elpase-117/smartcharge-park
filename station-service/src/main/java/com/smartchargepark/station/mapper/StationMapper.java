package com.smartchargepark.station.mapper;

import com.smartchargepark.station.model.Station;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.smartchargepark.station.amap.AmapPoi;
import java.time.LocalDateTime;

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

    @Update("""
            UPDATE station SET amap_poi_id=#{poi.id}, amap_name=#{poi.name}, amap_address=#{poi.address},
            longitude=#{poi.longitude}, latitude=#{poi.latitude}, district=#{poi.district},
            adcode=#{poi.adcode}, amap_type=#{poi.type}, amap_synced_at=#{syncedAt} WHERE id=#{id}
            """)
    int updatePoi(@Param("id") Long id, @Param("poi") AmapPoi poi, @Param("syncedAt") LocalDateTime syncedAt);
}
