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


-- First, extract ALL closed banners for ALL users (parse JSON only once per user)
WITH all_closed_banners AS (
    SELECT 
        s.user_id,
        jt.banner_id
    FROM storage s,
         JSON_TABLE(s.storage_data, '$[*]' COLUMNS (banner_id NUMBER PATH '$')) jt
    WHERE s.storage_key = 'closedBanners'
)
SELECT 
    b.id as banner_id,
    b.product,
    b.style,
    b.subject,
    u.name as user_name,
    u.token as user_token
FROM banner b
JOIN all_closed_banners acb ON acb.banner_id = b.id
JOIN users u ON u.id = acb.user_id
WHERE b.id = 3;

