SELECT 
    b.id as banner_id,
    b.product,
    b.style,
    b.subject,
    s.user_id,
    u.name as user_name,
    u.token as user_token
FROM banner b
JOIN storage s ON s.id IN (
    SELECT s2.id 
    FROM storage s2,
         JSON_TABLE(s2.storage_data, '$[*]' COLUMNS (banner_id NUMBER PATH '$')) jt
    WHERE jt.banner_id = b.id
)
JOIN users u ON u.id = s.user_id
WHERE b.id = 3;  -- Replace with your banner ID