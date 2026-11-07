package com.smartchargepark.station;

import com.smartchargepark.station.mapper.StationMapper;
import com.smartchargepark.station.model.Station;
import com.smartchargepark.station.service.StationQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StationQueryServiceTest {
    private final StationMapper mapper = mock(StationMapper.class);
    private final StationQueryService service = new StationQueryService(mapper);

    @Test
    void blankKeywordListsAllExistingStations() {
        Station station = new Station();
        station.setId(4L);
        station.setPowerSummary("超充 360.0 kW；快充 250.0 kW");
        when(mapper.findAll(null)).thenReturn(List.of(station));
        when(mapper.findById(4L)).thenReturn(station);

        assertThat(service.list("   ")).containsExactly(station);
        assertThat(service.detail(4L).getPowerSummary()).isEqualTo("超充 360.0 kW；快充 250.0 kW");
    }

    @Test
    void keywordIsTrimmedBeforeBoundQuery() {
        service.list(" 小鹏 ");
        verify(mapper).findAll("小鹏");
    }

    @Test
    void missingStationReturnsNotFound() {
        assertThatThrownBy(() -> service.detail(99999L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
