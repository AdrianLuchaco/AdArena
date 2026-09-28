# Subir AdArena a internet (gratis), paso a paso

> Tiempo aproximado: 1 hora y media la primera vez. Todo con planes **gratuitos**, sin dominio propio.
> Los paneles de estas webs cambian a veces de aspecto: si un botón se llama un poco distinto, busca
> el más parecido. Si te atascas, mándame una captura de la pantalla (sin contraseñas ni claves).

## Índice

0. [Qué vas a usar (y por qué)](#0-qué-vas-a-usar-y-por-qué)
1. [Prepara dos claves](#1-prepara-dos-claves)
2. [Sube el código a GitHub](#2-sube-el-código-a-github)
3. [Base de datos: Supabase](#3-base-de-datos-supabase)
4. [Emails: Brevo](#4-emails-brevo)
5. [Backend: Render](#5-backend-render)
6. [Web: Vercel](#6-web-vercel)
7. [Une las piezas](#7-une-las-piezas)
8. [Que no se duerma nunca: UptimeRobot](#8-que-no-se-duerma-nunca-uptimerobot)
9. [Pruébalo todo](#9-pruébalo-todo)
10. [El día a día](#10-el-día-a-día)
11. [Límites de los planes gratuitos](#11-límites-de-los-planes-gratuitos)
12. [Cuando quieras dominio propio y AdSense](#12-cuando-quieras-dominio-propio-y-adsense)
13. [Si algo falla](#13-si-algo-falla)

---

## 0. Qué vas a usar (y por qué)

```
            tu navegador
                 │
                 ▼
   ┌──────────────────────────┐   /api/*  (misma dirección:     ┌──────────────────────────┐
   │  WEB · Vercel            │ ─────────  la sesión funciona) ─▶│  BACKEND · Render         │
   │  adarena.vercel.app      │                                  │  adarena-api.onrender.com │
   └──────────────────────────┘   tiempo real (WebSocket) ──────▶│  Java · Fráncfort          │
                                  directo al backend             └──────┬──────────┬────────┘
                                                                        │          │ emails (HTTPS)
                                                                        ▼          ▼
                                                    ┌──────────────────────┐  ┌──────────┐
                                                    │ BASE DE DATOS        │  │  Brevo   │
                                                    │ Supabase · Fráncfort │  └──────────┘
                                                    └──────────────────────┘
      UptimeRobot visita el backend cada 5 min: así Render no lo duerme y el cierre de medianoche
      se hace siempre a su hora.
```

| Pieza | Servicio | Qué te da gratis |
|---|---|---|
| Código | **GitHub** | Repositorio privado y comprobaciones automáticas (tests) |
| Base de datos | **Supabase** | PostgreSQL de 500 MB, siempre encendido |
| Backend (Java) | **Render** | 512 MB de memoria, 750 horas al mes (da para estar encendido todo el mes) |
| Web (Next.js) | **Vercel** | La web en `algo.vercel.app`, con HTTPS |
| Emails | **Brevo** | 300 emails al día, sin dominio propio |
| Vigilancia | **UptimeRobot** | Visita el backend cada 5 min y te avisa por email si se cae |

**Por qué estos y no otros:**
- **Brevo por HTTPS y no por SMTP:** el plan gratuito de Render bloquea los puertos de correo (25, 465 y 587). El backend ahora sabe enviar por la API de Brevo, que va por HTTPS.
- **La web reenvía la API (`/api/*`):** sin dominio propio, la web (`.vercel.app`) y el backend (`.onrender.com`) son "sitios distintos" y el navegador bloquearía la cookie de sesión: tendrías que volver a entrar cada vez que recargas. Con el reenvío, para el navegador todo es la misma web. (Está en `frontend/src/proxy.ts`.)
- **Supabase y no otros:** siempre encendido, sin límite de horas. El backend consulta la base de datos cada pocos segundos (para cerrar la Arena a su hora), y otros planes gratuitos se quedarían sin horas.

---

## 1. Prepara dos claves

> **Atajo:** en la carpeta del proyecto tienes dos ficheros que no se suben a GitHub: **`.env.render`**
> y **`.env.vercel`** (en el Finder, pulsa Cmd + Mayús + . para ver los ficheros ocultos). Ya llevan el
> `PROXY_SECRET` generado e igual en los dos. Rellena lo que pone `RELLENAR` a medida que avances, y al
> final pega cada fichero entero en su panel: en Render, **Environment → Add from .env**; en Vercel,
> pégalo en el primer campo "Key" de Environment Variables (lo separa solo). Si usas este atajo, no
> hace falta que generes el `PROXY_SECRET` de abajo.

Abre la app **Terminal** y ejecuta:

```bash
openssl rand -hex 32
```

Te sale algo como `9f2c…e41a` (64 caracteres). Es tu **PROXY_SECRET**: una clave que comparten la web y
el backend. Guárdala en tus notas (la usarás dos veces).

Piensa también una **contraseña de administrador** larga (mínimo 12 caracteres) para tu cuenta de admin
en la web de verdad. No uses la de pruebas (`AdminAdArena2026!`).

> Nunca pegues claves ni contraseñas en GitHub, en un chat ni en un email. Solo en los paneles de
> Render y Vercel.

---

## 2. Sube el código a GitHub

El código ya está guardado en git en tu ordenador (rama `main`). Solo falta subirlo.

### 2.1 Crea el repositorio

1. Entra en **github.com** (crea una cuenta si no tienes).
2. Arriba a la derecha, **+ → New repository**.
3. **Repository name:** `adarena`. Marca **Private**.
4. **No** marques "Add a README", ni .gitignore, ni licencia (ya los tiene).
5. **Create repository**.

### 2.2 Súbelo

**Opción A (la más fácil): con la herramienta de GitHub.** En la Terminal:

```bash
brew install gh            # si no tienes Homebrew: https://brew.sh (una línea que copias y pegas)
gh auth login              # elige: GitHub.com → HTTPS → Login with a web browser (te da un código)
cd ~/Documents/AdArena
git remote add origin https://github.com/TU_USUARIO/adarena.git
git push -u origin main
```

**Opción B: sin instalar nada.** Los mismos comandos sin `brew` ni `gh`. Cuando `git push` te pida
contraseña, **no** es tu contraseña de GitHub sino un **token**: en GitHub, tu foto → **Settings →
Developer settings → Personal access tokens → Fine-grained tokens → Generate new token**, con acceso
solo al repositorio `adarena` y permiso **Contents: Read and write**. Pega ese token como contraseña.

Recarga la página del repositorio en GitHub: verás todas las carpetas. En la pestaña **Actions** verás
que se ejecutan las comprobaciones (tardan unos 10 minutos; al acabar, ✓ verde).

---

## 3. Base de datos: Supabase

1. Entra en **supabase.com → Start your project** (puedes entrar con tu cuenta de GitHub).
2. **New project**:
   - **Name:** `adarena`
   - **Database Password:** pulsa **Generate a password** y **cópiala a tus notas** (no se vuelve a ver).
   - **Region:** **Central EU (Frankfurt)**.
   - Si aparece una opción **"Enable Data API"** (a veces en "Security options"), **desmárcala**.
   - **Create new project** y espera un par de minutos.
3. **Apaga la "API automática" de Supabase** (AdArena no la usa, y así nadie puede entrar a las tablas
   por ahí): menú **Integrations → Data API** (o Project Settings → Data API) → desactiva **Enable Data
   API** → guarda. (Además, el backend le quita todos los permisos al crear las tablas: es una segunda
   barrera, migración V12).
4. Arriba, botón **Connect** → pestaña **Connection string** → elige **Session pooler** (¡no "Direct
   connection"!: Render no tiene IPv6 y la directa no funcionaría). Verás algo como:

   ```
   postgresql://postgres.abcdefghijklmn:[YOUR-PASSWORD]@aws-0-eu-central-1.pooler.supabase.com:5432/postgres
   ```

   De ahí sacas tres valores para Render (apúntalos):

   | Variable | Valor (ejemplo) |
   |---|---|
   | `DB_URL` | `jdbc:postgresql://aws-0-eu-central-1.pooler.supabase.com:5432/postgres?sslmode=require` (el host y el puerto de TU cadena, con `jdbc:` delante y `?sslmode=require` al final) |
   | `DB_USERNAME` | `postgres.abcdefghijklmn` (lo que va antes de los dos puntos) |
   | `DB_PASSWORD` | la contraseña que generaste en el paso 2 |

No tienes que crear tablas: el backend las crea solo la primera vez que arranca.

---

## 4. Emails: Brevo

1. Entra en **brevo.com → Sign up free**. Te pedirá algunos datos (nombre, empresa: puedes poner
   "AdArena") y verificar tu email.
2. **Verifica el remitente** (la dirección desde la que saldrán los emails): menú de tu cuenta →
   **Senders, domains & dedicated IPs → Senders → Add a sender**. Nombre: `AdArena`; email: el tuyo
   (por ejemplo tu Gmail). Te llega un email: confírmalo.
   - Consejo: crea un Gmail solo para la web (por ejemplo `adarena.avisos@gmail.com`).
3. **Crea la clave de la API:** menú de tu cuenta → **SMTP & API → API Keys → Generate a new API key**
   (nombre: `adarena-render`). Copia la clave (empieza por `xkeysib-`): es tu **BREVO_API_KEY**.
4. El paso de "IPs autorizadas" de Brevo lo harás en el paso 7, cuando tengas el backend.

> Sin dominio propio, algunos emails pueden llegar a la carpeta de **spam**. Con un dominio (paso 12)
> se soluciona.

---

## 5. Backend: Render

1. Entra en **render.com → Get started** con tu cuenta de **GitHub** y dale acceso al repositorio
   `adarena`.
2. **New → Blueprint** → elige el repositorio `adarena` → Render lee el archivo `render.yaml` y te
   enseña el servicio **adarena-api** (plan Free, Frankfurt).
3. Te pide estos valores:

   | Variable | Qué pones |
   |---|---|
   | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Los de Supabase (paso 3) |
   | `PROXY_SECRET` | La clave del paso 1 |
   | `FRONTEND_ORIGINS` | De momento `https://adarena.vercel.app` (lo corregirás en el paso 7 si tu web acaba con otra dirección) |
   | `PUBLIC_URL` | Lo mismo que `FRONTEND_ORIGINS` |
   | `ADMIN_EMAIL` | Tu email (será tu cuenta de administrador) |
   | `ADMIN_PASSWORD` | La contraseña de admin que pensaste en el paso 1 |
   | `BREVO_API_KEY` | La clave de Brevo (paso 4) |
   | `MAIL_FROM` | `AdArena <el-email-que-verificaste-en-brevo@gmail.com>` |

   `JWT_SECRET` lo genera Render solo.
4. **Apply / Deploy.** La primera vez tarda **10–15 minutos**: unos 5 en compilar y otros 5 en arrancar
   (el plan gratuito tiene muy poca CPU; es normal). En **Logs**, cuando todo va bien, verás:
   - `Successfully applied 12 migrations` (crea las tablas en Supabase)
   - `Started AdArenaApplication in … seconds`
   - `Admin account … created`
5. Arriba verás la dirección de tu backend, algo como **`https://adarena-api.onrender.com`** (si ese
   nombre ya existía, Render añade letras: usa la tuya en todo lo que sigue). Ábrela añadiendo
   `/actuator/health`: debe decir `{"status":"UP"…}`.

---

## 6. Web: Vercel

1. Entra en **vercel.com → Sign up** con tu cuenta de **GitHub** (plan **Hobby**, gratis).
2. **Add New… → Project** → importa el repositorio `adarena`.
3. Configura:
   - **Project Name:** `adarena` (tu web será `https://adarena.vercel.app`; si está cogido, Vercel te
     propone otro).
   - **Root Directory:** pulsa **Edit** y elige la carpeta **`frontend`** (¡importante!).
   - **Framework Preset:** Next.js (lo detecta solo).
   - **Environment Variables** (añade las cuatro):

     | Nombre | Valor |
     |---|---|
     | `NEXT_PUBLIC_API_URL` | `/` (solo una barra) |
     | `NEXT_PUBLIC_WS_URL` | `wss://adarena-api.onrender.com/ws` (con TU dirección de Render, empezando por `wss://` y acabando en `/ws`) |
     | `BACKEND_URL` | `https://adarena-api.onrender.com` (TU dirección de Render, sin barra final) |
     | `PROXY_SECRET` | La MISMA clave del paso 1 |

4. **Deploy.** Tarda 1–2 minutos. Al acabar, pulsa la imagen de la web o **Visit**: esa es tu dirección
   (por ejemplo `https://adarena.vercel.app`). Cópiala.

---

## 7. Une las piezas

**7.1 Dile al backend cuál es la dirección de la web.** En Render → tu servicio **adarena-api →
Environment**: pon tu dirección de Vercel exacta (con `https://`, sin barra final) en
`FRONTEND_ORIGINS` y en `PUBLIC_URL` → **Save changes**. Render reinicia el backend (unos 5 minutos).
Si ya coincidía con la que pusiste en el paso 5, no hace falta.

**7.2 Autoriza a Render en Brevo.** Brevo bloquea las llamadas desde direcciones IP que no conoce.
1. En Render → **adarena-api → Connect** (arriba a la derecha) → pestaña **Outbound** → copia las
   direcciones IP que aparecen.
2. En Brevo → menú de tu cuenta → **Security → Authorized IPs** → añade cada una de esas IP.

(La alternativa, "Deactivate blocking", funciona pero es menos segura: cualquiera que robara tu clave
podría usarla.)

---

## 8. Que no se duerma nunca: UptimeRobot

Render duerme el backend gratuito tras 15 minutos sin visitas, y tarda varios minutos en despertar. Si
estuviera dormido a medianoche, la Arena se cerraría tarde. Solución gratuita:

1. Entra en **uptimerobot.com → Register for free**.
2. **+ New monitor**:
   - **Monitor Type:** HTTP(s)
   - **Friendly Name:** AdArena API
   - **URL:** `https://adarena-api.onrender.com/actuator/health` (la tuya)
   - **Monitoring Interval:** 5 minutes
   - Alertas: tu email.
3. **Create monitor.** Desde ahora lo visita cada 5 minutos (nunca se duerme) y, si se cae, te llega
   un email.

---

## 9. Pruébalo todo

1. Abre tu web (`https://adarena.vercel.app`). Verás la portada con la Arena abierta (sin pujas: es una
   base de datos nueva, sin datos de ejemplo).
2. **Log in** con tu `ADMIN_EMAIL` y tu `ADMIN_PASSWORD` → arriba aparece **Admin**.
3. **Recarga la página:** debes seguir dentro (eso comprueba que la sesión por el reenvío funciona).
4. En otra ventana privada, **Sign up** con otro email tuyo → debes tener 200 puntos.
5. **Emails:** en esa cuenta nueva, sal y usa **Forgot your password?** → te debe llegar el email
   (mira también en spam). Si no llega, mira el paso 13.
6. Crea un anuncio (**Account → My ad**) con tu web y puja en la **Arena**.
7. A medianoche (hora de Madrid) se cierra sola. Al día siguiente, en **Admin → Moderation**, apruebas
   al ganador y sale en la portada.

**Pásame, si quieres que lo revise:** la dirección de tu web y la de tu backend, y si algo falla, una
captura o las líneas de **Logs** de Render con el error. **Nunca** me pases contraseñas ni claves (ni
tú ni nadie debería necesitarlas).

---

## 10. El día a día

- **Cambiar algo de la web:** haces el cambio en tu ordenador, lo guardas en git y lo subes:
  ```bash
  git add -A
  git commit -m "Describe el cambio"
  git push
  ```
  GitHub pasa los tests; Vercel publica la web en 1–2 minutos; Render, el backend en ~10 minutos. Solo
  se actualiza lo que ha cambiado.
- **Ver qué pasa en el backend:** Render → adarena-api → **Logs**.
- **Ver la base de datos:** Supabase → **Table Editor** (solo mirar; los cambios, desde la web de admin).
- **Copia de seguridad** (recomendado cada semana; el plan gratuito de Supabase no te deja descargar
  las suyas). Con Docker abierto, en la Terminal (usa tus datos del paso 3):
  ```bash
  docker run --rm -e PGPASSWORD='TU_CONTRASEÑA' postgres:17-alpine \
    pg_dump -h aws-0-eu-central-1.pooler.supabase.com -p 5432 -U postgres.TU_REFERENCIA -d postgres \
    --no-owner --no-privileges > adarena-copia-$(date +%F).sql
  ```
  Guarda ese archivo `.sql` en un sitio seguro (no en GitHub: contiene los emails de los usuarios).
- **En tu ordenador todo sigue igual:** `./dev.sh` (con los datos de ejemplo). Lo de la nube y lo local
  no se mezclan.

---

## 11. Límites de los planes gratuitos

| Servicio | Límite | Qué significa para AdArena |
|---|---|---|
| Render | 0,1 CPU y 512 MB; 750 h/mes; se duerme sin visitas | Cada actualización del backend tarda ~10 min en estar lista y durante ese rato la web puede fallar al cargar datos. UptimeRobot evita que se duerma (un mes tiene como mucho 744 h: cabe). Si algún día va lento con muchos usuarios, el plan de pago más barato de Render sube la CPU |
| Supabase | 500 MB; se pausa si pasa 1 semana sin actividad | Las imágenes van en la base de datos (se reducen a menos de 2 MB): da para cientos de anuncios. Nunca estará una semana sin actividad (el backend la consulta cada pocos segundos) |
| Vercel Hobby | Solo uso **no comercial**; 1 millón de peticiones al mes | Mientras no haya anuncios, perfecto. **Los anuncios de AdSense cuentan como uso comercial** (ver paso 12) |
| Brevo | 300 emails al día | De sobra para empezar. Sin dominio, algunos irán a spam |
| GitHub Actions | 2.000 minutos al mes (repositorio privado) | Cada subida gasta ~12 minutos: unas 150 subidas al mes |
| UptimeRobot | 50 monitores, cada 5 min | Usas 1 |

---

## 12. Cuando quieras dominio propio y AdSense

1. **Dominio** (lo único que cuesta dinero: ~10 €/año en Namecheap, Cloudflare, Porkbun…). Por
   ejemplo `adarena.com`.
2. **Conéctalo a Vercel:** Vercel → proyecto → **Settings → Domains → Add** y sigue los pasos (te dice
   qué registros DNS poner en tu proveedor). Después, en Render, añade el dominio nuevo a
   `FRONTEND_ORIGINS` (separado por coma) y cámbialo en `PUBLIC_URL`.
3. **Emails con tu dominio en Brevo:** Senders, domains → **Domains → Add a domain** → añade los
   registros DNS que te da. Cambia `MAIL_FROM` a `AdArena <avisos@adarena.com>`. Ya no irán a spam.
4. **AdSense** exige un dominio propio y es **uso comercial**: el plan gratuito de Vercel no lo
   permite. Opciones: pagar Vercel Pro (20 $/mes) o mover la web a un alojamiento gratuito que sí
   permita anuncios (lo haríamos juntos). Después, las variables `NEXT_PUBLIC_ADSENSE_*` en Vercel
   (ver `frontend/.env.example` y FASE-09 §15).

---

## 13. Si algo falla

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| Render: `Connection refused` / `UnknownHost` / `timeout` con la base de datos | Usaste la conexión "Direct" de Supabase | Usa la de **Session pooler** (paso 3) |
| Render: `password authentication failed` | `DB_USERNAME` sin la referencia, o contraseña mal copiada | El usuario es `postgres.xxxxx`. Si no recuerdas la contraseña: Supabase → Project Settings → Database → **Reset database password** |
| Render: `Could not resolve placeholder 'JWT_SECRET'` | Falta la variable | Environment → añade `JWT_SECRET` (cualquier texto aleatorio largo: `openssl rand -base64 48`) |
| Render: se reinicia en bucle con `OutOfMemoryError` o `Killed` | Poca memoria | Avísame con las líneas del log |
| La web carga pero sale "We can’t load the homepage" | El backend está arrancando (tras una actualización) o `BACKEND_URL` está mal | Espera 5–10 min. Si sigue, revisa `BACKEND_URL` en Vercel (sin barra final) |
| Entras y al recargar te pide entrar otra vez | `NEXT_PUBLIC_API_URL` no es `/` | Ponlo en `/` en Vercel y **Redeploy** (Deployments → ⋯ → Redeploy) |
| "Connecting…" en vez de "Live now" | `NEXT_PUBLIC_WS_URL` mal, o `FRONTEND_ORIGINS` no coincide con la dirección de la web | `wss://…onrender.com/ws` en Vercel (y Redeploy); en Render, `FRONTEND_ORIGINS` exactamente igual que la dirección de tu web |
| Al pujar o guardar sale un error de "origin" | `FRONTEND_ORIGINS` no coincide | Igual que arriba |
| No llegan los emails; en Logs: `Brevo answered 401` | IP no autorizada o clave mal copiada | Paso 7.2; o genera otra clave en Brevo |
| En Logs: `Brevo answered 400 … sender` | El remitente de `MAIL_FROM` no está verificado en Brevo | Paso 4.2, y que `MAIL_FROM` use exactamente ese email |
| Los emails llegan a spam | Sin dominio propio | Paso 12 |
| GitHub → Actions con ✗ roja | Algún test ha fallado | Abre el detalle y mándame el error |
