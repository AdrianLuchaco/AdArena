-- =====================================================================
-- AdArena pasa a llamarse LaunchCrown: la Arena es ahora la Race y los Arena Points, Crown Points.
-- Los avisos ya enviados se renombran para que el panel de avisos use el nombre nuevo.
-- (El historial de puntos no se toca: el libro de movimientos no se puede modificar, a propósito.)
-- =====================================================================

UPDATE notifications
SET title = regexp_replace(replace(replace(title, 'Arena Points', 'Crown Points'), 'AdArena', 'LaunchCrown'),
                           '\mArena\M', 'Race', 'g'),
    body  = regexp_replace(replace(replace(body, 'Arena Points', 'Crown Points'), 'AdArena', 'LaunchCrown'),
                           '\mArena\M', 'Race', 'g'),
    link  = CASE WHEN link = '/arena' OR link LIKE '/arena?%' OR link LIKE '/arena#%'
                 THEN '/race' || substr(link, 7) ELSE link END
WHERE title LIKE '%Arena%' OR body LIKE '%Arena%' OR link LIKE '/arena%';
