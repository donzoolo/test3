UPDATE T_ALERT t
SET status = 'Closed'
WHERE t.creation_timestamp < SYSDATE - 5
  AND t.workflow = 'ABC'
  AND t.ROWID IN (
    SELECT rid FROM (
      SELECT ROW_NUMBER() OVER (
              PARTITION BY business_unit_id
              ORDER BY creation_timestamp ASC
             ) AS rn,
             t1.ROWID AS rid
      FROM T_ALERT t1
      WHERE t1.creation_timestamp < SYSDATE - 5
        AND t1.workflow = 'ABC'
    )
    WHERE rn > 100
  );