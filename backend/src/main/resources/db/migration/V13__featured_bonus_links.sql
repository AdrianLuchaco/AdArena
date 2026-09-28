-- =====================================================================
-- Bonus links destacados y enlaces iniciales.
--  - Un enlace destacado sale el primero en Bonus links, marcado, y da más puntos. Lo decide el
--    admin desde Admin → Promotions (botón "Feature").
--  - Los enlaces a YouTube y X que ya tenga el administrador pasan a destacados con 100 puntos.
--  - Se añaden unos enlaces de programación y startups (20 puntos) a nombre del administrador, para
--    que Bonus links no esté vacío al lanzar. Si todavía no hay administrador (base de datos nueva,
--    como en local o en los tests), no se añade nada.
-- =====================================================================

ALTER TABLE social_tasks ADD COLUMN featured boolean NOT NULL DEFAULT false;

UPDATE social_tasks t
SET featured = true, reward_points = 100
FROM users u
WHERE u.id = t.owner_id AND u.role = 'ADMIN' AND t.platform IN ('YOUTUBE', 'X');

INSERT INTO social_tasks (id, owner_id, platform, title, description, url, status, reward_points)
SELECT gen_random_uuid(), admin.id, link.platform, link.title, link.description, link.url, 'ACTIVE', 20
FROM (SELECT id FROM users WHERE role = 'ADMIN' ORDER BY created_at LIMIT 1) AS admin
CROSS JOIN (VALUES
    ('WEB', 'Hacker News', 'The front page of the startup and programming world, run by Y Combinator.',
     'https://news.ycombinator.com'),
    ('WEB', 'Product Hunt', 'The best new products and startups, launched every day by their makers.',
     'https://www.producthunt.com'),
    ('GITHUB', 'GitHub Trending', 'The open-source repositories developers are starring today.',
     'https://github.com/trending'),
    ('YOUTUBE', 'Fireship', 'Fast, fun videos about programming, frameworks and the latest in tech.',
     'https://www.youtube.com/@Fireship'),
    ('WEB', 'roadmap.sh', 'Step-by-step guides to become a frontend, backend or DevOps developer.',
     'https://roadmap.sh'),
    ('WEB', 'Indie Hackers', 'Founders sharing how they build and grow profitable online businesses.',
     'https://www.indiehackers.com')
) AS link (platform, title, description, url)
WHERE NOT EXISTS (SELECT 1 FROM social_tasks s WHERE s.url = link.url);
