# Fase 12 — Lista para subirla gratis a la nube

> Fecha: 2026-09-28. Estado: ✅ preparada (falta que tú sigas **[docs/DESPLIEGUE.md](../DESPLIEGUE.md)**).
> Tests del backend: **264 en verde** (258 + 6 nuevos). Web: `tsc`, `eslint` y `next build` sin errores.
> Probado "como en la nube" en tu ordenador: backend en Docker con **512 MB y 0,1 CPU** (lo mismo que el
> plan gratuito de Render) y la web en modo producción reenviando `/api/*`.

## Índice

1. [Lo que pediste y qué se hizo](#1-lo-que-pediste-y-qué-se-hizo)
2. [Qué servicios y por qué](#2-qué-servicios-y-por-qué)
3. [Problema 1: la sesión sin dominio propio](#3-problema-1-la-sesión-sin-dominio-propio)
4. [Problema 2: los límites contra abusos detrás del reenvío](#4-problema-2-los-límites-contra-abusos-detrás-del-reenvío)
5. [Problema 3: los emails (Render bloquea SMTP)](#5-problema-3-los-emails-render-bloquea-smtp)
6. [Problema 4: la API automática de Supabase](#6-problema-4-la-api-automática-de-supabase)
7. [Problema 5: 512 MB y 0,1 CPU](#7-problema-5-512-mb-y-01-cpu)
8. [Herramientas de desarrollo quitadas](#8-herramientas-de-desarrollo-quitadas)
9. [GitHub: código, comprobaciones automáticas y Dependabot](#9-github-código-comprobaciones-automáticas-y-dependabot)
10. [Archivo por archivo](#10-archivo-por-archivo)
11. [Tests y verificación](#11-tests-y-verificación)
12. [Lo que tienes que hacer tú](#12-lo-que-tienes-que-hacer-tú)
13. [Límites conocidos](#13-límites-conocidos)

---

## 1. Lo que pediste y qué se hizo

| Pediste | Qué se hizo |
|---|---|
| Subir backend, web y base de datos a la nube, **gratis** | Supabase (base de datos) + Render (backend) + Vercel (web) + UptimeRobot (que no se duerma). Archivos listos: `render.yaml`, `frontend/vercel.json`, `.env.example`, `frontend/.env.example` |
| Quitar lo que era solo para desarrollo | Borrados los puntos de prueba, el botón "End the Arena now" y el recuadro de AdSense de muestra (web y backend) |
| Enviar emails gratis | Brevo (300/día, sin dominio), por su API HTTPS: el backend ya sabe usarla |
| Subirlo a GitHub | Todo guardado en git en la rama `main`, listo para `git push`; comprobaciones automáticas y Dependabot configurados |
| "Dime exactamente todo lo que tengo que hacer" | **[docs/DESPLIEGUE.md](../DESPLIEGUE.md)**: 13 apartados, con cada botón, cada variable y una tabla de problemas frecuentes |

## 2. Qué servicios y por qué

| Pieza | Servicio | Gratis | Por qué este |
|---|---|---|---|
| Base de datos | Supabase | 500 MB, siempre encendida | El backend consulta cada 5 s (cierre de la Arena): Neon (por horas de cálculo) se quedaría sin horas. Supabase no tiene límite de horas |
| Backend | Render | 512 MB, 0,1 CPU, 750 h/mes | Docker desde GitHub; Railway y Fly.io ya no tienen plan gratuito |
| Web | Vercel | Hobby | Los creadores de Next.js. **Solo uso no comercial**: con AdSense habrá que pagar Pro o mover la web |
| Emails | Brevo | 300/día | Funciona sin dominio y tiene API HTTPS (Resend exige dominio) |
| Vigilancia | UptimeRobot | Cada 5 min | Evita que Render duerma el backend y avisa si se cae |

Regiones: backend y base de datos en **Fráncfort** (cerca de España); la web, también en Fráncfort (`vercel.json`).

## 3. Problema 1: la sesión sin dominio propio

La sesión de 30 días va en una cookie. Sin dominio, la web (`adarena.vercel.app`) y el backend
(`adarena-api.onrender.com`) son **sitios distintos** y los navegadores bloquean esa cookie: habría que
entrar de nuevo cada vez que recargas.

**Solución:** `frontend/src/proxy.ts` (el "Proxy" de Next.js 16, antes Middleware). Cada petición a
`/api/*` de la web se reenvía al backend (`BACKEND_URL`). Para el navegador la API está en la misma
dirección que la web (`NEXT_PUBLIC_API_URL=/`), así que la cookie es propia. El **tiempo real
(WebSocket)** va directo al backend (`NEXT_PUBLIC_WS_URL`): no usa cookies (se autentica con el token).

En tu ordenador `BACKEND_URL` no existe y todo funciona como siempre (la web llama a `localhost:8081`).

**Comprobado** con la web en modo producción: `/api/public/home` responde a través de la web, el login
deja la cookie `adarena_refresh` en la dirección de la web (`Path=/api/auth; Secure; HttpOnly;
SameSite=Lax`), al recargar sigues dentro y la Arena marca "Live now".

## 4. Problema 2: los límites contra abusos detrás del reenvío

El backend limita intentos por IP (por ejemplo, 10 logins por minuto). Detrás del reenvío, todas las
peticiones llegarían desde Vercel: **todos los usuarios compartirían el límite** y un atacante podría
bloquear el login de todo el mundo.

**Solución:** la web añade dos cabeceras al reenviar: la IP real del visitante y una clave compartida
(`PROXY_SECRET`, la misma en Render y Vercel). El backend (`RateLimitFilter.clientIp`) solo cree esa IP
si la clave coincide (comparación en tiempo constante, clave de 16+ caracteres, y la IP debe tener
forma de IP). Sin la clave, se usa la IP de la conexión: nadie puede inventarse una IP.

**Comprobado:** a través de la web, el visitante A queda limitado (`429`) y el B no; directo al backend
con la cabecera falsa, se ignora. Tests: `RateLimitFilterTest` (4).

## 5. Problema 3: los emails (Render bloquea SMTP)

La documentación de Render lo dice: *"Free web services can't send outbound network traffic on ports
25, 465, or 587"*. El SMTP no funcionaría.

**Solución:** `BrevoMailDelivery`, que envía cada email con una petición HTTPS a la API de Brevo
(`BREVO_API_KEY`). Si Brevo responde con error, la bandeja de salida lo reintenta más tarde (1, 2, 4,
8… minutos), igual que antes. El SMTP sigue disponible para cuando tengas un plan de pago o un dominio.
El panel de admin sigue diciendo si los emails salen de verdad.

Tests: `BrevoMailDeliveryTest` (2), con un servidor falso que hace de Brevo: comprueba la clave, el
remitente, el destinatario y el contenido, y que un error de Brevo lanza el reintento.

## 6. Problema 4: la API automática de Supabase

Supabase publica sola una API REST sobre las tablas del esquema `public`. AdArena no la usa, pero si
se quedara abierta, alguien podría leer o cambiar tablas directamente.

**Solución (dos barreras):**
1. Migración **V12**: quita todos los permisos de los roles `anon` y `authenticated` sobre nuestras
   tablas, secuencias y funciones, también sobre las futuras. En una base de datos normal (tu
   ordenador, los tests) esos roles no existen y no hace nada. Probada a mano con roles simulados:
   antes tenían permisos; después, ninguno (ni en una tabla creada después).
2. La guía te pide desactivar la **Data API** en el panel de Supabase.

## 7. Problema 5: 512 MB y 0,1 CPU

- **Memoria:** `JAVA_OPTS` en el `Dockerfile` ajustado para 512 MB (heap al 55 %, recolector Serial,
  compilación ligera, límites de metaspace y caché de código). **Medido:** ~380 MB en marcha.
- **CPU:** con 0,1 CPU, la primera vez tarda **~5,5 min en arrancar** (crea las tablas); las siguientes,
  algo menos. Por eso cada actualización del backend tarda ~10 min (compilar + arrancar).
- **Que no se duerma:** Render duerme el servicio tras 15 min sin visitas y despertar tardaría minutos.
  **UptimeRobot** lo visita cada 5 min: nunca se duerme y el cierre de medianoche es puntual. Un mes
  tiene como mucho 744 h y el plan da 750.
- **Base de datos:** `DB_POOL_SIZE=5` (el pooler gratuito admite pocas conexiones).

## 8. Herramientas de desarrollo quitadas

| Quitado | Dónde |
|---|---|
| "Only on your computer: test points" (+500, +2.000, +10.000) | `PointsView.tsx`, `api.ts` y `DevWalletController.java` (borrado) |
| "End the Arena now" | `AdminOverviewView.tsx`, `api.ts` y `DevToolsController.java` (borrado) |
| Recuadro discontinuo "AdSense ad space" | `AdSenseUnit.tsx` (sin AdSense configurado ya no ocupa nada, tampoco en local) |

Se mantienen, porque solo existen en tu ordenador (perfil `dev`) y nunca en la nube: los **datos de
ejemplo** (4 anunciantes, ganador, Arena con pujas) y el Swagger. Los tests que comprueban que esas
rutas no existen fuera de desarrollo siguen pasando (ahora dan 404 siempre).

Consecuencia: en local, para probar el cierre tienes que esperar a medianoche (o te añado un comando
de desarrollo aparte si lo echas de menos).

También: el mensaje de "demasiadas peticiones" (429) estaba aún en español → inglés; el nombre por
defecto del admin pasa de "Administrador" a "Admin".

## 9. GitHub: código, comprobaciones automáticas y Dependabot

- Todo el trabajo (fases 2 a 12) queda guardado en git en la rama **`main`** (antes solo existía el
  commit "Fase 1", en `master`, que se renombra a `main`). Falta que tú lo subas (DESPLIEGUE §2).
- `.github/workflows/ci.yml`: en cada subida, GitHub ejecuta los tests del backend (con PostgreSQL de
  verdad) y compila la web. Solo permisos de lectura.
- `.github/dependabot.yml`: cada semana, GitHub te propone actualizaciones de las librerías (sobre
  todo de seguridad).
- `.gitignore`: se añade `.remember/` (notas locales de herramientas).

## 10. Archivo por archivo

| Archivo | Cambio |
|---|---|
| `render.yaml` | **Nuevo**: el backend en Render (Docker, Free, Fráncfort, health check, variables) |
| `frontend/vercel.json` | **Nuevo**: región Fráncfort |
| `frontend/src/proxy.ts` | **Nuevo**: reenvía `/api/*` al backend con la IP real y la clave |
| `frontend/src/lib/config.ts`, `realtime.ts`, `next.config.ts` | `NEXT_PUBLIC_WS_URL` y API en la misma dirección (`/`); CSP |
| `backend/.../notification/service/BrevoMailDelivery.java` | **Nuevo**: emails por la API de Brevo |
| `backend/.../notification/service/MailConfig.java`, `common/config/AppProperties.java`, `application.yml` | `BREVO_API_KEY`, `PROXY_SECRET`, admin "Admin" |
| `backend/.../security/ratelimit/RateLimitFilter.java` | IP real si llega la clave; mensaje 429 en inglés |
| `backend/src/main/resources/db/migration/V12__lock_down_supabase_api_roles.sql` | **Nueva** migración |
| `backend/Dockerfile` | Opciones de Java para 512 MB |
| `DevToolsController.java`, `DevWalletController.java` | **Borrados** |
| `frontend/src/components/panel/PointsView.tsx`, `admin/AdminOverviewView.tsx`, `ads/AdSenseUnit.tsx`, `lib/api.ts` | Sin herramientas de desarrollo |
| `.env.example`, `frontend/.env.example` | Reescritos para Render/Supabase/Brevo/Vercel |
| `.github/workflows/ci.yml`, `.github/dependabot.yml` | **Nuevos** |
| `docs/DESPLIEGUE.md` | **Nuevo**: la guía paso a paso |
| Tests | `BrevoMailDeliveryTest` (2), `RateLimitFilterTest` (4), `SchemaMigrationTest` (12 migraciones) |

## 11. Tests y verificación

- **Backend:** `./mvnw clean test` → **264 tests, 0 fallos**.
- **Web:** `tsc`, `eslint` (0 avisos) y `next build` correctos (aparece `ƒ Proxy (Middleware)`).
- **"Como en la nube", en tu ordenador** (todo parado y borrado después):
  - Imagen Docker del backend con `--memory=512m --cpus=0.1`, perfil de producción, base de datos
    nueva: 12 migraciones, admin creado, ronda abierta; ~380 MB; ~5,5 min en arrancar.
  - Web en modo producción (`next build` + `next start`) con `BACKEND_URL` y `PROXY_SECRET`: API por la
    web, cookie propia, sesión que sobrevive a recargar, tiempo real conectado, límites por visitante.
  - V12 probada con roles `anon`/`authenticated` simulados.
- **No probado de verdad** (necesita tus cuentas): Supabase, Render, Vercel y Brevo reales. La guía
  tiene una tabla de problemas frecuentes; si algo falla, mándame las líneas del log.

## 12. Lo que tienes que hacer tú

Seguir **[docs/DESPLIEGUE.md](../DESPLIEGUE.md)** en orden. Resumen:

1. `openssl rand -hex 32` → tu `PROXY_SECRET`. Piensa una contraseña de admin larga.
2. GitHub: crea el repositorio privado `adarena` y `git push -u origin main`.
3. Supabase: proyecto en Fráncfort, **desactiva la Data API**, copia la conexión "Session pooler".
4. Brevo: verifica tu email como remitente y crea la API key.
5. Render: New → Blueprint → rellena las variables → espera ~10–15 min.
6. Vercel: importa el repositorio con **Root Directory = `frontend`** y 4 variables.
7. Render: pon la dirección real de Vercel en `FRONTEND_ORIGINS` y `PUBLIC_URL`; Brevo: autoriza las IP
   de salida de Render.
8. UptimeRobot: monitor cada 5 min a `/actuator/health`.
9. Pruébalo (entrar, recargar, registrarse, recuperar contraseña, pujar).

**Qué pasarme si algo falla:** las direcciones de tu web y de tu backend, y capturas o líneas de los
Logs de Render/Vercel con el error. **Nunca** contraseñas ni claves.

## 13. Límites conocidos

- Cada actualización del backend tarda ~10 min y durante ese rato la web no puede cargar datos (no hay
  "cero caídas" en el plan gratuito).
- Emails sin dominio: algunos irán a spam.
- AdSense: necesita dominio propio y no está permitido en el plan gratuito de Vercel.
- El plan gratuito de Supabase no permite descargar sus copias de seguridad: haz la tuya semanal con
  `pg_dump` (DESPLIEGUE §10).
- La subida de imágenes admite hasta 5 MB; no he podido comprobar si el reenvío de Vercel acepta
  cuerpos tan grandes. Si una imagen grande da error en la nube, avísame y bajo el límite.
