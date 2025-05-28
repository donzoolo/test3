SELECT 
    b.id as banner_id,
    b.product,
    b.style,
    b.subject,
    LISTAGG(s.user_id, ', ') WITHIN GROUP (ORDER BY s.user_id) as users_who_closed
FROM banner b
LEFT JOIN storage s ON JSON_EXISTS(s.storage_data, '$[*]?(@ == ' || b.id || ')')
WHERE b.id = :banner_id  -- Replace with your parameter
GROUP BY b.id, b.product, b.style, b.subject;