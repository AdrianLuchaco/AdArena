# Subir AdArena a internet, paso a paso

> Todo gratis salvo el **dominio** (≈ 10 $ al año, opcional pero recomendado).
> Tiempo: unas 2 horas la primera vez. Hazlo en orden: cada paso usa datos del anterior.
> Los paneles de estas webs cambian a veces de aspecto: si un botón se llama un poco distinto, busca
> el más parecido. Si te atascas, mándame una captura (sin contraseñas ni claves).

## Índice

0. [El plan y lo que cuesta](#0-el-plan-y-lo-que-cuesta)
1. [Tus dos ficheros de claves](#1-tus-dos-ficheros-de-claves)
2. [Sube el código a GitHub](#2-sube-el-código-a-github)
3. [Base de datos: Supabase](#3-base-de-datos-supabase)
4. [Emails: Brevo](#4-emails-brevo)
5. [Backend: Render](#5-backend-render)
6. [Web: Vercel](#6-web-vercel)
7. [Une las piezas](#7-une-las-piezas)
8. [Que no se duerma nunca: UptimeRobot](#8-que-no-se-duerma-nunca-uptimerobot)
9. [Tu dominio (≈ 10 $/año)](#9-tu-dominio--10-año)
10. [Email con tu dominio (gratis)](#10-email-con-tu-dominio-gratis)
11. [Pruébalo todo](#11-pruébalo-todo)
12. [Anuncios con Ezoic](#12-anuncios-con-ezoic)
13. [El día a día](#13-el-día-a-día)
14. [Límites de los planes gratuitos](#14-límites-de-los-planes-gratuitos)
15. [Si algo falla](#15-si-algo-falla)

---

## 0. El plan y lo que cuesta

```
            tu navegador ──▶ tudominio.com
                 │
                 ▼
   ┌──────────────────────────┐   /api/*  (misma dirección:     ┌──────────────────────────┐
   │  WEB · Vercel            │ ─────────  la sesión funciona) ─▶│  BACKEND · Render         │
   │  tudominio.com           │                                  │  adarena-api.onrender.com │
   └──────────────────────────┘   tiempo real (WebSocket) ──────▶│  Java · Fráncfort          │
                                  directo al backend             └──────┬──────────┬────────┘
                                                                        │          │ emails (HTTPS)
                                                                        ▼          ▼
                                                    ┌──────────────────────┐  ┌──────────┐
                                                    │ BASE DE DATOS        │  │  Brevo   │
                                                    │ Supabase · Fráncfort │  └──────────┘
                                                    └──────────────────────┘
      Cloudflare: te vende el dominio, gestiona sus "DNS" y reenvía hola@tudominio.com a tu Gmail.
      UptimeRobot: visita el backend cada 5 min para que Render no lo duerma.
```

| Pieza | Servicio | Precio |
|---|---|---|
| Código | GitHub | Gratis |
| Base de datos | Supabase | Gratis (500 MB) |
| Backend (Java) | Render | Gratis (512 MB) |
| Web (Next.js) | Vercel | Gratis **mientras no haya anuncios** (ver §12) |
| Emails automáticos de la web | Brevo | Gratis (300 al día) |
| Vigilancia | UptimeRobot | Gratis |
| **Dominio** | Cloudflare | **≈ 10 $ al año** (`.com`, el mismo precio al renovar) |
| Email con tu dominio (recibir y enviar) | Cloudflare + Brevo + tu Gmail | Gratis |

**Por qué así:**
- **La web reenvía la API (`/api/*`):** sin eso, el navegador bloquearía la cookie de sesión entre la web y el backend y tendrías que volver a entrar cada vez que recargas. (Está en `frontend/src/proxy.ts`; funciona con dominio y sin él).
- **Brevo por HTTPS:** el plan gratuito de Render bloquea los puertos de correo (SMTP).
- **Supabase:** siempre encendido y sin límite de horas (el backend consulta la base de datos cada pocos segundos para cerrar la Arena a su hora).

---

## 1. Tus dos ficheros de claves

En la carpeta del proyecto (`Documents/AdArena`) hay dos ficheros **que nunca se suben a GitHub**:

| Fichero | Para | Cómo se usa al final |
|---|---|---|
| **`.env.render`** | El backend (Render) | Copias cada valor en Render (paso 5) |
| **`.env.vercel`** | La web (Vercel) | Lo pegas entero en Vercel (paso 6) |

**Cómo abrirlos:** son ficheros "ocultos" (empiezan por punto).
- Desde la Terminal: `open -e ~/Documents/AdArena/.env.render` (se abre en TextEdit). Igual con `.env.vercel`.
- O en el Finder, dentro de la carpeta `AdArena`, pulsa **Cmd + Mayús + .** para ver los ocultos.

**`PROXY_SECRET` ya está generado** e igual en los dos: no lo toques. Vas sustituyendo cada `RELLENAR`
(o `TU-BACKEND`) según avances. Esta tabla dice de dónde sale cada valor:

### `.env.render`

| Variable | Qué es | Dónde lo encuentras | Paso |
|---|---|---|---|
| `DB_URL` | Dirección de la base de datos | Supabase → **Connect** → **Session pooler** | 3 |
| `DB_USERNAME` | Usuario de la base de datos (`postgres.xxxx`) | Supabase → Connect → Session pooler | 3 |
| `DB_PASSWORD` | Contraseña de la base de datos | La que generas al crear el proyecto de Supabase | 3 |
| `DB_POOL_SIZE` | Conexiones a la base de datos | Déjalo en `5` | — |
| `PROXY_SECRET` | Clave compartida web ↔ backend | Ya está puesta | — |
| `FRONTEND_ORIGINS` | La dirección de tu web | La de Vercel (paso 6) y, luego, tu dominio (paso 9) | 6 y 9 |
| `PUBLIC_URL` | La dirección de tu web para los enlaces de los emails | Igual que la de arriba (una sola) | 6 y 9 |
| `ADMIN_EMAIL` | Tu email de administrador en la web | Te lo inventas tú (el tuyo) | — |
| `ADMIN_PASSWORD` | Tu contraseña de administrador (mínimo 12 caracteres) | Te la inventas tú (larga y que no uses en otro sitio) | — |
| `ADMIN_NAME` | Tu nombre visible | Déjalo en `Admin` o pon el tuyo | — |
| `BREVO_API_KEY` | Clave para enviar emails | Brevo → **SMTP & API → API Keys** | 4 |
| `MAIL_FROM` | Remitente de los emails | `AdArena <tu-email-verificado-en-brevo>`; con dominio, `AdArena <avisos@tudominio.com>` | 4 y 10 |

(`JWT_SECRET` no está: Render lo genera solo).

### `.env.vercel`

| Variable | Qué es | Valor |
|---|---|---|
| `NEXT_PUBLIC_API_URL` | Dónde está la API | Déjalo en `/` |
| `NEXT_PUBLIC_WS_URL` | El tiempo real | `wss://TU-BACKEND.onrender.com/ws` → cambia `TU-BACKEND` por lo que te dé Render (paso 5) |
| `BACKEND_URL` | A dónde reenvía la web | `https://TU-BACKEND.onrender.com` (lo mismo) |
| `PROXY_SECRET` | Clave compartida | Ya está puesta |
| `NEXT_PUBLIC_EZOIC_ENABLED` | Anuncios de Ezoic | Déjalo en `false` (paso 12) |

> Nunca pegues estas claves en GitHub, en un chat ni en un email. Solo en los paneles de Render y
> Vercel. Si alguna vez se filtran, genera otras (en Supabase, Brevo…) y cámbialas en los paneles.

---

## 2. Sube el código a GitHub

El código ya está guardado en git en tu ordenador (rama `main`). Solo falta subirlo.

### 2.1 Crea el repositorio
1. Entra en **github.com** (crea una cuenta si no tienes).
2. Arriba a la derecha, **+ → New repository**.
3. **Repository name:** `adarena`. Marca **Private**.
4. **No** marques "Add a README", ni .gitignore, ni licencia.
5. **Create repository**. Apunta tu nombre de usuario de GitHub (sale en la dirección: `github.com/TU_USUARIO/adarena`).

### 2.2 Súbelo
En la Terminal (cambia `TU_USUARIO`):

```bash
cd ~/Documents/AdArena
git remote add origin https://github.com/TU_USUARIO/adarena.git
git push -u origin main
```

Te pedirá usuario y **contraseña**. La contraseña **no** es la de GitHub, sino un **token**:
1. En GitHub: tu foto (arriba a la derecha) → **Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token**.
2. **Token name:** `mi-mac`. **Expiration:** 1 año. **Repository access:** *Only select repositories* → `adarena`.
3. **Permissions → Repository permissions → Contents:** *Read and write*.
4. **Generate token**, cópialo y pégalo como contraseña. El Mac lo recuerda para la próxima vez.

(Alternativa: `brew install gh` y `gh auth login`, que lo hace con el navegador).

Recarga la página del repositorio: verás todas las carpetas. En **Actions** verás las comprobaciones
automáticas (unos 10 minutos; al acabar, ✓ verde).

---

## 3. Base de datos: Supabase

1. Entra en **supabase.com → Start your project** (puedes entrar con tu cuenta de GitHub).
2. **New project**:
   - **Name:** `adarena`
   - **Database Password:** pulsa **Generate a password**, cópiala y pégala en `.env.render` como
     `DB_PASSWORD` (no se vuelve a ver).
   - **Region:** **Central EU (Frankfurt)**.
   - Si aparece **"Enable Data API"** (a veces dentro de "Security options"), **desmárcala**.
   - **Create new project** y espera un par de minutos.
3. **Apaga la "API automática" de Supabase** (AdArena no la usa; así nadie puede entrar a las tablas
   por ahí): menú de la izquierda **Integrations → Data API** (o **Project Settings → Data API**) →
   desactiva **Enable Data API** → guarda. (El backend además le quita los permisos al crear las
   tablas: es una segunda barrera).
4. **Copia la conexión:** botón **Connect** (arriba) → pestaña **Connection string** → tipo **URI** →
   en "Method" elige **Session pooler** (¡no "Direct connection": no funcionaría desde Render!).
   Verás algo así:

   ```
   postgresql://postgres.abcdefghijklmn:[YOUR-PASSWORD]@aws-0-eu-central-1.pooler.supabase.com:5432/postgres
                └──── DB_USERNAME ────┘                 └────────────── host ──────────────┘ └puerto┘
   ```

   En `.env.render`:
   - `DB_USERNAME=postgres.abcdefghijklmn` (lo que va entre `//` y `:`)
   - `DB_URL=jdbc:postgresql://aws-0-eu-central-1.pooler.supabase.com:5432/postgres?sslmode=require`
     (empieza por `jdbc:postgresql://`, luego **tu** host y puerto, y termina en `/postgres?sslmode=require`;
     sin usuario ni contraseña dentro)

No crees tablas: el backend las crea solo al arrancar la primera vez.

---

## 4. Emails: Brevo

1. **brevo.com → Sign up free.** Te pedirá algunos datos (en empresa puedes poner "AdArena") y
   confirmar tu email.
2. **Remitente:** menú de tu cuenta (arriba a la derecha) → **Senders, domains & dedicated IPs →
   Senders → Add a sender**. Nombre `AdArena`, email: el tuyo (por ejemplo tu Gmail). Te llega un email:
   confírmalo. En `.env.render`: `MAIL_FROM=AdArena <ese-email>`.
   - Cuando tengas dominio (paso 10) lo cambiarás a `avisos@tudominio.com`.
3. **Clave de la API:** menú de tu cuenta → **SMTP & API** → pestaña **API Keys** → **Generate a new API
   key** (nombre `adarena-render`). Cópiala (empieza por `xkeysib-`) y pégala en `.env.render` como
   `BREVO_API_KEY`.
4. Las "IPs autorizadas" de Brevo las harás en el paso 7.

---

## 5. Backend: Render

1. **render.com → Get started**, entra con tu cuenta de **GitHub** y dale acceso al repositorio
   `adarena` (Render → Account Settings → GitHub, si no te lo pregunta).
2. **New → Blueprint** → elige el repositorio `adarena` → Render lee el archivo `render.yaml` y te
   muestra el servicio **adarena-api** (plan Free, Frankfurt).
3. Te pide los valores con un recuadro vacío: **cópialos uno a uno de `.env.render`** (solo lo que va
   detrás del `=`). En `FRONTEND_ORIGINS` y `PUBLIC_URL` deja de momento `https://adarena.vercel.app`.
4. **Apply / Deploy.** La primera vez tarda **10–15 minutos** (5 en compilar y 5 en arrancar: el plan
   gratuito tiene muy poca CPU). En la pestaña **Logs**, cuando va bien, verás:
   - `Successfully applied 12 migrations` (ha creado las tablas en Supabase)
   - `Started AdArenaApplication in … seconds`
   - `Admin account … created`
5. **Tu dirección del backend** sale arriba, bajo el nombre, algo como `https://adarena-api.onrender.com`
   (si el nombre estaba cogido, lleva letras al final). Compruébalo: ábrela añadiendo
   `/actuator/health` → debe decir `{"status":"UP",…}`.
6. En `.env.vercel`, cambia `TU-BACKEND` por lo tuyo en las dos líneas (por ejemplo `adarena-api`).

> Si más adelante cambias algo en `.env.render`, pásalo a Render → **adarena-api → Environment** (ahí
> puedes editar cada variable, o usar **Add from .env** y pegar el fichero entero) → **Save changes**.

---

## 6. Web: Vercel

1. **vercel.com → Sign up** con tu cuenta de **GitHub** (plan **Hobby**, gratis).
2. **Add New… → Project** → **Import** el repositorio `adarena`.
3. Configura antes de desplegar:
   - **Project Name:** `adarena` (la web será `https://adarena.vercel.app`; si está cogido, pon otro).
   - **Root Directory:** pulsa **Edit** y elige **`frontend`** (¡importante!).
   - **Framework Preset:** Next.js (lo detecta solo).
   - **Environment Variables:** abre `.env.vercel`, **selecciona todo y cópialo**, y pégalo en el
     primer campo **Key**: Vercel lo separa solo en 5 variables. Comprueba que salen las 5 (si alguna
     línea de comentario, las que empiezan por `#`, aparece como variable, bórrala) y que
     `NEXT_PUBLIC_WS_URL` y `BACKEND_URL` llevan tu dirección de Render.
4. **Deploy** (1–2 minutos). Pulsa la imagen de la web: esa es tu dirección (por ejemplo
   `https://adarena.vercel.app`).

---

## 7. Une las piezas

**7.1 El backend tiene que conocer la dirección de la web.** Si tu web **no** es exactamente
`https://adarena.vercel.app`: en `.env.render` pon la tuya en `FRONTEND_ORIGINS` y `PUBLIC_URL`, y en
Render → **adarena-api → Environment** cámbialas también → **Save changes** (reinicia en ~5 min).

**7.2 Autoriza a Render en Brevo.** Brevo bloquea las llamadas desde IPs que no conoce.
1. Render → **adarena-api** → botón **Connect** (arriba a la derecha) → pestaña **Outbound** → copia las
   direcciones IP.
2. Brevo → menú de tu cuenta → **Security → Authorized IPs** → añade cada una.

---

## 8. Que no se duerma nunca: UptimeRobot

Render duerme el backend gratuito tras 15 minutos sin visitas (y despertar tarda minutos). Si estuviera
dormido a medianoche, la Arena se cerraría tarde.

1. **uptimerobot.com → Register for free.**
2. **+ New monitor** → **HTTP(s)** → URL: `https://TU-BACKEND.onrender.com/actuator/health` →
   intervalo **5 minutes** → alertas a tu email → **Create monitor**.

Ahora la web ya funciona en `adarena.vercel.app`. Los pasos 9 y 10 son para tener tu dominio.

---

## 9. Tu dominio (≈ 10 $/año)

**Dónde comprarlo: Cloudflare.** Vende los dominios a precio de coste (un `.com` ronda los **10 $ al
año** y **renovar cuesta lo mismo**; en otros sitios el primer año es barato y luego sube mucho),
incluye la privacidad de tus datos y te da gratis los DNS y el reenvío de emails (paso 10).

> Consejo: los `.com` suben de precio el 1 de noviembre de 2026; si lo compras antes, te ahorras un
> poco el primer año.

### 9.1 Cómpralo
1. **dash.cloudflare.com → Sign up** (necesitarás una tarjeta).
2. Menú **Domain Registration → Register Domains** → busca el nombre (por ejemplo `adarena`) y mira qué
   terminaciones están libres (`.com` es lo mejor; `.app` o `.io` son más caras).
3. Añádelo al carrito, rellena tus datos (Cloudflare los oculta del registro público) y paga.
4. En unos minutos aparece en **Websites** (o "Domains") de tu cuenta de Cloudflare.

### 9.2 Conéctalo a la web (Vercel)
1. Vercel → proyecto **adarena → Settings → Domains → Add** → escribe `tudominio.com` → **Add**. Vercel
   te propone añadir también `www.tudominio.com` y redirigirlo al principal: acepta.
2. Vercel te enseña los registros DNS que necesita (normalmente un registro **A** para `@` y un
   **CNAME** para `www`). Déjalo abierto.
3. Cloudflare → tu dominio → **DNS → Records → Add record**, y crea exactamente los que te pide
   Vercel. En cada uno, **Proxy status: DNS only** (la nube **gris**, no naranja).
4. Vuelve a Vercel: en unos minutos los dominios salen con ✓ (**Valid Configuration**) y con
   certificado HTTPS.

### 9.3 Dile al backend tu nueva dirección
En `.env.render` y en Render → **Environment**:
- `FRONTEND_ORIGINS=https://tudominio.com,https://www.tudominio.com`
- `PUBLIC_URL=https://tudominio.com`

→ **Save changes**. Los enlaces de los emails ya llevarán tu dominio.

---

## 10. Email con tu dominio (gratis)

Tres partes, todas gratis. Hazlas en este orden.

### 10.1 Que los emails de la web salgan desde `avisos@tudominio.com` (Brevo)
1. Brevo → **Senders, domains & dedicated IPs → Domains → Add a domain** → `tudominio.com`.
2. Brevo te da unos registros DNS (un TXT de verificación, la firma **DKIM** y un **DMARC**). Si te
   ofrece configurarlo **automáticamente con Cloudflare**, úsalo; si no, créalos a mano en Cloudflare →
   **DNS → Records → Add record** (tipo y valor exactos que te da Brevo).
3. En Brevo pulsa **Authenticate / Verify**: cuando salga en verde, el dominio está listo.
4. En `.env.render` y en Render → Environment: `MAIL_FROM=AdArena <avisos@tudominio.com>` → **Save changes**.

Con esto los emails de la web dejan de ir a spam.

### 10.2 Recibir los emails de `hola@tudominio.com` en tu Gmail (Cloudflare)
1. Cloudflare → tu dominio → **Email → Email Routing** → **Get started / Enable**.
2. **Custom address:** `hola` → **Destination:** tu Gmail → confirma el email que te llega a Gmail.
3. Cloudflare añade solo los registros que necesita (MX y SPF).
4. (Opcional) **Catch-all**: todo lo que llegue a `cualquiercosa@tudominio.com`, a tu Gmail.

**Importante (SPF):** solo puede haber **un** registro TXT que empiece por `v=spf1`. Si Brevo también
te pidió uno, júntalos en uno solo:
`v=spf1 include:_spf.mx.cloudflare.net include:spf.brevo.com ~all`

### 10.3 Responder desde Gmail como `hola@tudominio.com` (opcional)
1. Brevo → **SMTP & API** → pestaña **SMTP** → apunta el **servidor** (`smtp-relay.brevo.com`), el
   **puerto** (`587`) y el **login**, y pulsa **Generate a new SMTP key** (es la contraseña).
2. Gmail → ⚙️ **Ver todos los ajustes → Cuentas e importación → Enviar como → Añadir otra dirección**
   → `hola@tudominio.com` → servidor, puerto, login y clave SMTP de Brevo → TLS.
3. Gmail envía un código a `hola@tudominio.com`, que te llega a tu Gmail gracias al paso 10.2.

Ahora escribes y respondes desde Gmail con tu dirección profesional. (Comparte el límite de Brevo: 300
emails al día entre la web y tú).

---

## 11. Pruébalo todo

1. Abre tu web (`https://tudominio.com` o la de Vercel). Verás la Arena abierta, sin pujas (es una base
   de datos nueva: sin datos de ejemplo).
2. **Log in** con tu `ADMIN_EMAIL` y `ADMIN_PASSWORD` → arriba aparece **Admin**.
3. **Recarga la página:** debes seguir dentro.
4. En una ventana privada, **Sign up** con otro email tuyo → debes tener 200 puntos.
5. **Email:** en esa cuenta, sal y usa **Forgot your password?** → te debe llegar (mira también en spam).
6. Crea un anuncio (**Account → My ad**) y puja en la **Arena**.
7. Tras la medianoche (hora de Madrid), en **Admin → Moderation** apruebas al ganador y sale en la portada.

**Pásame, si quieres que lo revise:** la dirección de tu web y la del backend, y si algo falla, una
captura o las líneas de **Logs** con el error. **Nunca** contraseñas ni claves.

---

## 12. Anuncios con Ezoic

El código ya está preparado: los huecos están puestos y **se activan con una sola variable**. Pero antes
hay dos cosas que debes saber:

1. **Ezoic no acepta webs nuevas pequeñas.** Desde febrero de 2026 pide **250.000 usuarios al mes**. Para
   webs más pequeñas tiene el programa **Incubator**, que elige unas 20 webs al mes según su potencial.
   Solicítalo cuando la web tenga algo de movimiento; si no te aceptan a la primera, vuelve a probar
   cuando crezca.
2. **Vercel gratis no permite anuncios** (cualquier anuncio cuenta como "uso comercial"). El día que
   actives Ezoic tienes que elegir: pagar **Vercel Pro (20 $/mes)** o **mover la web a otro alojamiento
   gratuito que sí los permita** (por ejemplo Netlify). Avísame antes y lo hacemos juntos.

**Cuando Ezoic te acepte:**
1. **Cuenta:** ezoic.com → **Sign up** → añade `tudominio.com`. Método de integración: **JavaScript**
   (ya está en el código; no cambies los DNS de tu dominio a Ezoic).
2. **ads.txt:** ya funciona solo: `https://tudominio.com/ads.txt` redirige al gestor de Ezoic en cuanto
   actives los anuncios. En el panel de Ezoic, en la sección de ads.txt, elige la opción de redirección.
3. **Crea los huecos** (en Ezoic se llaman *placeholders*) con **estos números**, que son los que usa la
   web:

   | Número | Dónde está |
   |---|---|
   | 101 | Portada, al final |
   | 102 | La Arena, bajo la clasificación |
   | 103 | Promote, bajo la cabecera |
   | 104 | Promote, columna derecha (escritorio) |
   | 105 | Promote, columna derecha, segundo (escritorio) |
   | 106 | Promote sin sesión (móvil) |
   | 107 | Promote, bajo el formulario |
   | 108 | Promote, entre tus promociones |
   | 109 | Promote, al final |

4. **Desactiva en Ezoic los formatos automáticos**: anuncio fijo abajo (*anchor*), pantalla completa
   (*interstitial/vignette*), laterales (*side rails*) y vídeo flotante. La web ya los desactiva, pero
   hazlo también en el panel: si no, podrían salir en las páginas donde se ganan puntos, y eso no se
   permite (los puntos nunca pueden depender de ver anuncios).
5. **Aviso de cookies:** lo pone Ezoic solo (es obligatorio en Europa). Revisa en su panel que enlace a
   tu política de privacidad (`https://tudominio.com/legal/privacy`) y, si Ezoic te da un texto para
   añadir a esa política, pásamelo y lo pongo.
6. **Actívalo:** Vercel → proyecto → **Settings → Environment Variables** → `NEXT_PUBLIC_EZOIC_ENABLED`
   = `true` → **Save** → **Deployments → ⋯ (el último) → Redeploy**. (Y en `.env.vercel`, cámbialo también
   a `true` para tenerlo apuntado).

---

## 13. El día a día

- **Cambiar algo de la web:** haces el cambio en tu ordenador y lo subes:
  ```bash
  git add -A
  git commit -m "Describe el cambio"
  git push
  ```
  GitHub pasa los tests; Vercel publica la web en 1–2 minutos; Render, el backend en ~10 minutos.
- **Ver qué pasa en el backend:** Render → adarena-api → **Logs**.
- **Ver la base de datos:** Supabase → **Table Editor** (solo mirar; los cambios, desde el admin de la web).
- **Copia de seguridad semanal** (el plan gratuito de Supabase no te deja descargar las suyas). Con
  Docker abierto, en la Terminal, con tus datos del paso 3:
  ```bash
  docker run --rm -e PGPASSWORD='TU_DB_PASSWORD' postgres:17-alpine \
    pg_dump -h TU-HOST.pooler.supabase.com -p 5432 -U postgres.TU_REFERENCIA -d postgres \
    --no-owner --no-privileges > ~/Documents/adarena-copia-$(date +%F).sql
  ```
  Guárdala en un sitio seguro y **nunca** en GitHub (contiene los emails de los usuarios).
- **En tu ordenador todo sigue igual:** `./dev.sh`, con los datos de ejemplo. La nube y lo local no se mezclan.

---

## 14. Límites de los planes gratuitos

| Servicio | Límite | Qué significa |
|---|---|---|
| Render | 0,1 CPU, 512 MB, 750 h/mes, se duerme sin visitas | Cada actualización del backend tarda ~10 min y durante ese rato la web no carga datos. UptimeRobot evita que se duerma (un mes tiene como mucho 744 h: cabe) |
| Supabase | 500 MB; pausa si pasa 1 semana sin actividad | Da para cientos de anuncios. Nunca estará una semana parado (el backend lo consulta cada pocos segundos) |
| Vercel Hobby | Sin anuncios ni uso comercial | Ver §12 |
| Brevo | 300 emails al día | De sobra para empezar |
| Cloudflare | DNS y reenvío de emails gratis | Solo pagas el dominio |
| GitHub Actions | 2.000 min/mes (repositorio privado) | Unas 150 subidas al mes |

---

## 15. Si algo falla

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| Render: `Connection refused`, `UnknownHost` o `timeout` con la base de datos | Usaste la conexión "Direct" de Supabase | Usa la de **Session pooler** (paso 3) |
| Render: `password authentication failed` | Usuario sin la referencia, o contraseña mal copiada | El usuario es `postgres.xxxxx`. Si no recuerdas la contraseña: Supabase → Project Settings → Database → **Reset database password** |
| Render: `Could not resolve placeholder 'JWT_SECRET'` | Falta la variable | Environment → añade `JWT_SECRET` con `openssl rand -base64 48` |
| Render: se reinicia en bucle (`OutOfMemoryError`, `Killed`) | Poca memoria | Mándame las líneas del log |
| La web dice "We can’t load the homepage" | El backend está arrancando, o `BACKEND_URL` mal | Espera 10 min. Si sigue, revisa `BACKEND_URL` en Vercel (sin barra final) y **Redeploy** |
| Entras y al recargar te pide entrar otra vez | `NEXT_PUBLIC_API_URL` no es `/` | Ponlo en `/` en Vercel y **Redeploy** |
| "Connecting…" en vez de "Live now" | `NEXT_PUBLIC_WS_URL` mal, o `FRONTEND_ORIGINS` no coincide con la dirección de la web | `wss://…onrender.com/ws` en Vercel (y Redeploy); en Render, `FRONTEND_ORIGINS` exactamente igual que la dirección de tu web |
| Al pujar o guardar sale un error de "origin" | `FRONTEND_ORIGINS` no coincide | Igual que arriba (con dominio: las dos, con y sin `www`) |
| Vercel: el dominio no pasa a ✓ | Registros DNS mal o con la nube naranja | Revisa que sean exactamente los que pide Vercel y en **DNS only** (gris). Espera hasta 1 hora |
| No llegan los emails; en Logs: `Brevo answered 401` | IP no autorizada o clave mal copiada | Paso 7.2; o genera otra clave |
| En Logs: `Brevo answered 400 … sender` | El remitente de `MAIL_FROM` no está verificado | Paso 4.2 (o 10.1 con dominio) y que `MAIL_FROM` use exactamente ese email |
| Los emails llegan a spam | Sin dominio verificado en Brevo | Paso 10.1 |
| No te llegan los emails de `hola@tudominio.com` | Email Routing sin activar o sin confirmar el destino | Paso 10.2; mira que el SPF sea uno solo |
| GitHub → Actions con ✗ roja | Algún test ha fallado | Abre el detalle y mándame el error |
