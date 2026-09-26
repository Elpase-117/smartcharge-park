UPDATE station
SET snapshot_time = '2026-09-26 13:32'
WHERE id IN (1, 2, 3, 4)
  AND snapshot_time = '13:32（采集日期未显示）';

UPDATE station
SET snapshot_time = '2026-09-26 13:33'
WHERE id IN (5, 6)
  AND snapshot_time = '13:33（采集日期未显示）';

UPDATE station
SET snapshot_time = '2026-09-26 13:34'
WHERE id IN (7, 8, 9, 10)
  AND snapshot_time = '13:34（采集日期未显示）';
