# Fase 3 — Spring Boot 4, perfil de anuncio y la web de verdad

> **Estado:** ✅ terminada (2026-09-26)
> **Resultado verificable:**
> - `./dev.sh` → se abre **http://localhost:3000** con la web funcionando y datos de ejemplo.
> - `cd backend && ./mvnw test` → **88 tests, 0 fallos**.
> - `cd frontend && npm run lint && npm run build` → sin errores.
> - Comprobado visualmente en escritorio (1440 px) y en móvil (390 px) con capturas automáticas.

---

## Índice

1. [Lo primero: ¿qué es cada dirección?](#1-lo-primero-qué-es-cada-dirección)
2. [Cómo verlo en tu ordenador](#2-cómo-verlo-en-tu-ordenador)
3. [Migración a Spring Boot 4](#3-migración-a-spring-boot-4)
4. [Dos errores que encontré y corregí](#4-dos-errores-que-encontré-y-corregí)
5. [Backend: qué se ha construido](#5-backend-qué-se-ha-construido)
6. [La API nueva](#6-la-api-nueva)
7. [Frontend: la web](#7-frontend-la-web)
8. [Conceptos que necesitas entender](#8-conceptos-que-necesitas-entender)
9. [Archivo por archivo](#9-archivo-por-archivo)
10. [Configuración nueva](#10-configuración-nueva)
11. [Tests y verificación](#11-tests-y-verificación)
12. [Limitaciones conocidas y pendientes](#12-limitaciones-conocidas-y-pendientes)
13. [Siguiente fase](#13-siguiente-fase)

---

## 1. Lo primero: ¿qué es cada dirección?

Publifi son **dos programas** que trabajan juntos:

| Dirección | Qué es | ¿Lo ven tus usuarios? |
|---|---|---|
| **http://localhost:3000** | **La web** (frontend, Next.js): páginas, botones, diseño | ✅ Sí: esto es Publifi |
| http://localhost:8081 | La **API** (backend, Spring Boot): datos, dinero y reglas | ❌ No directamente. La web le pide los datos por detrás |
| http://localhost:8081/swagger-ui.html | Herramienta técnica para probar la API | ❌ Solo para ti o para un programador |

Lo que viste ("Swagger UI") era esa herramienta técnica. **Desde esta fase, si abres `localhost:8081` te redirige automáticamente a la web**, para que no vuelva a pasar.

En producción será igual: `publifi.com` (la web, en Vercel) y `api.publifi.com` (la API, en Railway).

## 2. Cómo verlo en tu ordenador

### Paso 0 (solo esta vez): para el backend antiguo
Tienes abierta la versión de la fase 2 en una terminal (`./mvnw spring-boot:run`) y ocupa el puerto 8081. Ve a esa terminal y pulsa **Ctrl + C**. Además, reinicié la base de datos local para regenerar los datos de ejemplo (ver §4.2), así que esa versión antigua ya no funcionaría bien.

### Paso 1: arrancarlo todo
Con **Docker Desktop abierto**, desde la carpeta del proyecto:
```bash
./dev.sh
```
El script:
1. comprueba que tienes Docker, Java y Node, y que los puertos están libres;
2. arranca PostgreSQL;
3. instala las dependencias de la web si faltan (solo la primera vez);
4. arranca la API y espera a que esté lista;
5. arranca la web y **abre el navegador en http://localhost:3000**.

Para pararlo todo: **Ctrl + C**.

### Cuentas de prueba (solo en tu ordenador)

| Cuenta | Email | Contraseña |
|---|---|---|
| Administrador | `admin@publifi.local` | `AdminPublifi2026!` |
| Anunciante (ganador de hoy) | `demo-cafe@publifi.local` | `DemoPublifi2026!` |
| Anunciante | `demo-bicis@publifi.local` | `DemoPublifi2026!` |
| Anunciante | `demo-lumen@publifi.local` | `DemoPublifi2026!` |
| Anunciante | `demo-huerta@publifi.local` | `DemoPublifi2026!` |

También puedes crearte una cuenta nueva desde "Crear cuenta".

### Qué puedes probar
- **Portada:** el anuncio de "Café Aurora" a pantalla completa. Arriba a la derecha, el botón **"En directo"** muestra cuánto le queda al anuncio y la clasificación de hoy. El botón "Visitar web" abre su web en otra pestaña.
- **Subasta de hoy:** contador grande y clasificación (Bicis Norte 45 €, Estudio Lumen 32 €, Huerta Viva 18 €). La puja aún está desactivada: llega en la fase 4.
- **Crear cuenta → Mi panel:** sube una imagen, escribe el nombre, la web y la descripción, y mira a la derecha la vista previa en directo de tu anuncio.
- **En el móvil:** todo está pensado primero para móvil. En el navegador del ordenador puedes simularlo con las herramientas de desarrollador (tecla F12 → icono del móvil).

## 3. Migración a Spring Boot 4

Me pediste pasar a Spring Boot 4. Estas son las versiones que cambiaron:

| Pieza | Antes | Ahora |
|---|---|---|
| Spring Boot | 3.5.16 | **4.1.1** |
| Spring Framework | 6.2 | **7.0** |
| Spring Security | 6.5 | **7.1** |
| Hibernate | 6.6 | **7.4** |
| Jackson (JSON) | 2.x (`com.fasterxml`) | **3.1** (`tools.jackson`) |
| Flyway | 11 | **12** |
| Testcontainers | 1.21 | **2.0** |
| springdoc (Swagger) | 2.8 | **3.1** |

**Qué hubo que cambiar en el código:**
1. **Nombres de dependencias** (`pom.xml`). Spring Boot 4 está dividido en módulos más pequeños:
   - `spring-boot-starter-web` → `spring-boot-starter-webmvc`
   - `spring-boot-starter-oauth2-resource-server` → `spring-boot-starter-security-oauth2-resource-server`
   - Flyway ahora necesita su propio starter: `spring-boot-starter-flyway`. Sin él, las migraciones no se ejecutarían.
   - Tests: `spring-boot-starter-webmvc-test`, `-data-jpa-test`, `-security-test`, y `testcontainers-postgresql` / `testcontainers-junit-jupiter`.
2. **Jackson 3:** `ProblemResponseWriter` pasa a usar `tools.jackson.databind.json.JsonMapper`.
3. **Tests:** `AutoConfigureMockMvc` cambió de paquete y `PostgreSQLContainer` se importa de `org.testcontainers.postgresql` (ya no es genérico).

**Resultado:** tras la migración pasaban los 42 tests existentes, sin avisos de código obsoleto. La imagen Docker también se construye y arranca correctamente.

## 4. Dos errores que encontré y corregí

Los cuento porque son lecciones útiles, y porque las redes de seguridad de la fase 1 hicieron su trabajo.

### 4.1 "Dos subastas abiertas a la vez"
Al crear los datos de ejemplo, el arranque falló con `duplicate key … ux_auctions_single_open`.
- **Causa:** Hibernate no ejecuta las órdenes SQL en el orden en que las escribes. Primero hace todos los **INSERT** y después los **UPDATE**. Yo creaba la subasta de ayer como abierta, la cerraba (un UPDATE pendiente) y creaba la de hoy (un INSERT). En la base de datos llegó primero el INSERT de hoy, cuando la de ayer seguía "abierta".
- **Quién lo paró:** el índice único de la fase 1 que prohíbe dos subastas abiertas. Sin él habría quedado un estado corrupto.
- **Solución:** forzar la escritura (`flush()`) del cierre antes de crear la subasta nueva. Lo tendré en cuenta en el cierre diario de la fase 6.
- Como todo iba en una transacción, no quedó nada a medias.

### 4.2 La hora de cierre desplazada una hora
El anuncio empezaba a las 01:00 de Madrid en lugar de a las 00:00.
- **Causa:** el ajuste `hibernate.jdbc.time_zone: UTC` de la fase 1. Con él, Hibernate convierte las horas "sueltas" (la columna `close_time`, de tipo `time`) entre zonas horarias y desplaza "00:00". Las fechas completas (`Instant`) no se veían afectadas.
- **Solución:** quitar ese ajuste (no hace falta para `Instant` con `timestamptz`) y añadir un test que comprueba que la hora de cierre se lee exactamente como se guardó.
- **Limpieza:** los datos de ejemplo ya se habían creado con la hora mala. Antes de tocar nada comprobé qué había en tu base de datos local: solo el admin automático y los 4 anunciantes de ejemplo, ningún dato tuyo. La reinicié (`docker compose down -v`) para regenerarlos bien. Los movimientos del ledger no se pueden borrar ni editar (protección de la fase 1), así que reiniciar era la forma limpia.

## 5. Backend: qué se ha construido

### 5.1 Imágenes seguras (`image/`)
Nunca guardamos ni servimos el archivo que sube el usuario tal cual. Cada imagen pasa por `ImageProcessor`:
1. **Se detecta el formato por el contenido**, no por el nombre ni por lo que dice el navegador. Se aceptan JPG, PNG, WebP y GIF (en el GIF, solo el primer fotograma). Un archivo "logo.png" que en realidad es otra cosa se rechaza.
2. **Se lee solo la cabecera** para conocer el tamaño. Se rechazan las imágenes de menos de 64 px de lado y las gigantes (más de 12 000 px de lado o 100 megapíxeles). Así nos protegemos de las "bombas de descompresión": archivos pequeños que ocupan gigas al abrirse.
3. **Se decodifica a resolución reducida** si la imagen es enorme, para gastar poca memoria, y se escala a un máximo de 1600 px por lado, reduciendo por pasos para mantener la calidad.
4. **Se vuelve a codificar desde cero:** PNG si tiene transparencia real y JPEG (calidad 86 %) en otro caso. Así desaparecen los metadatos (el GPS de una foto, el autor…) y cualquier contenido oculto.
5. Si aun así pesa más de 2 MB, se reduce más.

Para leer WebP y los JPEG en CMYK (habituales en logos hechos para imprenta) usé la librería **TwelveMonkeys ImageIO**, porque Java no los lee de serie.

Las imágenes se sirven en `/api/public/images/{id}` con **caché de un año** y ETag. Como son inmutables (cambiar el logo crea otra imagen), el navegador las guarda y no las vuelve a descargar.

### 5.2 Perfil de anuncio (`adprofile/`)
- `GET /api/me/ad-profile` → tu anuncio, o `404 AD_PROFILE_NOT_FOUND` si aún no existe.
- `PUT /api/me/ad-profile` → lo crea o lo actualiza.

**Validaciones:**

| Campo | Reglas |
|---|---|
| Empresa | Obligatorio, de 2 a 80 caracteres. Se limpian espacios, caracteres de control e invisibles |
| Web | Ver `WebsiteUrlSanitizer` abajo |
| Descripción | Obligatoria, de 10 a 300 caracteres. Se permiten saltos de línea (máximo una línea en blanco seguida) |
| Imagen | Obligatoria y **tuya**: no puedes usar una imagen que subió otra persona |

**`WebsiteUrlSanitizer`:** el botón "Visitar web" lleva a tus visitantes a esa dirección, así que:
- Solo `https://`. Si escribes "miweb.com", se añade `https://` automáticamente.
- Nada de IPs (`https://192.168.1.1`), `localhost`, dominios internos (`.local`) ni dominios sin extensión.
- Nada de usuario y contraseña en la URL (`https://usuario:clave@...`, un truco típico de phishing).
- Los dominios con tildes o eñes ("españa.es") se convierten a su forma técnica (punycode: `xn--espaa-rta.es`).

**`TextSanitizer`:**
- Normaliza Unicode (una "é" siempre se guarda igual).
- Quita caracteres de control y los caracteres invisibles que invierten la dirección del texto (se usan para disfrazar nombres), pero respeta los emojis compuestos.

Los errores de estos campos vuelven como `400 VALIDATION_FAILED` con un mensaje por campo, igual que los de Bean Validation. Para ello se creó `FieldValidationException`.

### 5.3 La portada (`home/`)
`GET /api/public/home` devuelve en una sola respuesta todo lo que necesita la portada:

| `state` | Cuándo | Qué muestra la web |
|---|---|---|
| `AD` | Hay un anuncio **aprobado** cuya ventana incluye este momento | El anuncio a pantalla completa |
| `PENDING_REVIEW` | Ayer hubo ganador, pero aún no lo has aprobado | Contenido base + "El anuncio de hoy está en revisión" |
| `NO_BIDS` | Ayer nadie pujó (regla 8) | Contenido base + "Hoy nadie ha pujado" |
| `NO_AD` | Cualquier otro caso (p. ej. todavía no hay subastas) | Contenido base |

Además incluye:
- `serverTime`: la hora del servidor, para que los contadores sean exactos aunque el reloj del usuario vaya mal.
- `auction`: la subasta abierta, con su fin (`endsAt`), el número de pujadores y el **top 10** del ranking. Orden: total de mayor a menor y, a igualdad, quien llegó antes (regla de desempate de la fase 1).

### 5.4 Datos de demostración (`demo/`)
**Solo en tu ordenador** (perfil `dev`) y **solo si la base de datos no tiene subastas**, se crea este escenario:
- 4 anunciantes con perfil e imagen. Las imágenes se dibujan por código: degradado, luces y una forma.
- **Subasta de ayer, cerrada:**
  - Café Aurora ganó con 60 € y su anuncio está aprobado y en portada.
  - Bicis Norte perdió con 40 €: perdió 20 € y arrastró 20 € (regla 7).
- **Subasta de hoy, abierta:**
  - Bicis Norte, 45 € (20 € arrastrados + 25 €).
  - Estudio Lumen, 32 €.
  - Huerta Viva, 18 €.

**Todo el dinero pasa por el ledger** (recargas, reservas, pérdida del 50 % y cobro al ganador). Un test comprueba que:
- todas las cuentas cuadran;
- tus ingresos son 80 € (60 + 20);
- el dinero reservado es 95 € (45 + 32 + 18).

Para desactivarlo: `DEMO_DATA=false`.

### 5.5 Otros cambios
- `GET /` en la API redirige a la web (`RootController`).
- Rate limiting:
  - **subida de imágenes:** 30 por hora e IP;
  - **rutas públicas** (`/api/public/**`): límite propio y más generoso, 600 por minuto, porque la portada se consulta a menudo;
  - la regla general admite ahora exclusiones (`exclude-path-prefixes`).
- Tamaño máximo de subida: 5 MB. Si se supera: `413 FILE_TOO_LARGE`.
- En local el backend usa el **8081** por defecto (perfil `dev`), también en Docker Compose.
- Repositorios nuevos que usarán las fases siguientes: `AuctionRepository`, `AuctionParticipationRepository` (con la consulta del ranking), `BidRepository` (secuencia de pujas), `AdSlotRepository`, `LedgerAccountRepository`, `LedgerTransactionRepository`, `AppSettingsRepository`, `StoredImageRepository` y `AdProfileRepository`.

## 6. La API nueva

| Método | Ruta | Acceso | Qué hace |
|---|---|---|---|
| GET | `/` | Público | Redirige a la web |
| GET | `/api/public/home` | Público | Datos de la portada |
| GET | `/api/public/images/{id}` | Público | Descarga una imagen (caché de un año) |
| POST | `/api/images` | Sesión | Sube una imagen (`multipart/form-data`, campo `file`) → `201` |
| GET | `/api/me/ad-profile` | Sesión | Tu anuncio (o `404 AD_PROFILE_NOT_FOUND`) |
| PUT | `/api/me/ad-profile` | Sesión | Crea o actualiza tu anuncio |

**Ejemplo de `/api/public/home`** (con los datos de ejemplo):
```json
{
  "serverTime": "2026-09-26T11:24:32Z",
  "state": "AD",
  "currentAd": {
    "companyName": "Café Aurora",
    "description": "Café de especialidad tostado cada semana…",
    "websiteUrl": "https://www.example.com/cafe-aurora",
    "imageUrl": "/api/public/images/a956c083-…",
    "startsAt": "2026-09-25T22:00:00Z",
    "endsAt": "2026-09-26T22:00:00Z"
  },
  "auction": {
    "auctionDate": "2026-09-27",
    "endsAt": "2026-09-26T22:00:00Z",
    "bidders": 3,
    "ranking": [
      { "position": 1, "companyName": "Bicis Norte", "totalCents": 4500 },
      { "position": 2, "companyName": "Estudio Lumen", "totalCents": 3200 },
      { "position": 3, "companyName": "Huerta Viva", "totalCents": 1800 }
    ]
  }
}
```
Las 22:00 UTC son las 00:00 de Madrid en horario de verano.

**Códigos de error nuevos:**

| HTTP | `code` | Cuándo |
|---|---|---|
| 400 | `INVALID_IMAGE` | El archivo no es una imagen válida |
| 400 | `IMAGE_TOO_SMALL` | Menos de 64 × 64 px |
| 400 | `IMAGE_DIMENSIONS_TOO_LARGE` | Dimensiones descomunales |
| 400 | `IMAGE_TOO_COMPLEX` | No se consigue reducir por debajo de 2 MB |
| 400 | `EMPTY_FILE` | No se ha enviado ningún archivo |
| 400 | `VALIDATION_FAILED` (+ `errors.websiteUrl`, `errors.imageId`…) | Datos del anuncio no válidos |
| 404 | `AD_PROFILE_NOT_FOUND` | Todavía no has creado tu anuncio |
| 404 | `IMAGE_NOT_FOUND` | Imagen inexistente |
| 413 | `FILE_TOO_LARGE` | Más de 5 MB |

## 7. Frontend: la web

### 7.1 Diseño
- **Estilo:**
  - fondo crema cálido (`#faf8f5`);
  - texto casi negro (`#16161d`);
  - un **naranja de marca** (`#ff5a1f`) enérgico, que encaja con la idea de "subasta";
  - esquinas muy redondeadas y sombras suaves.
- **Tipografías:** *Bricolage Grotesque* para los titulares (con carácter) y *Geist* para el texto (muy legible). Se cargan con `next/font`, que las sirve desde tu propio dominio: rápido y sin enviar datos a Google.
- **Móvil primero:** cada pantalla se diseñó para 390 px y crece a escritorio. Probado en ambos tamaños **sin desbordamiento horizontal**.
- **Todo en español**, con importes en formato español ("45,00 €") y horas de Madrid.
- **Accesibilidad:**
  - etiquetas en todos los campos y errores asociados a cada campo;
  - foco visible al navegar con teclado;
  - botones con `aria-label`/`aria-expanded`;
  - el desplegable se cierra con Escape.
- **Logotipo:** un cuadrado naranja con un "martillo de subasta" abstracto. También es el favicon (`app/icon.svg`).

### 7.2 Páginas

| Ruta | Qué hay |
|---|---|
| `/` | **Portada.** Con anuncio: imagen grande, nombre, descripción y botón "Visitar web" (pestaña nueva; nunca en un iframe, regla 10). El fondo es la misma imagen desenfocada, para envolverlo en sus colores. Arriba a la derecha, el desplegable **"En directo"**: tiempo que le queda al anuncio + clasificación de hoy + botón "Pujar por mañana". Abajo, una franja "¿Quieres aparecer aquí mañana? Puja ahora". Sin anuncio: titular "Esta portada se subasta cada día", maqueta de "Tu anuncio aquí" y el aviso que toque ("Hoy nadie ha pujado" o "en revisión"). Siempre, debajo: **"Cómo funciona"** en 3 pasos y la regla del 50 % explicada sin letra pequeña |
| `/subasta` | Contador grande (horas:minutos:segundos), hora de cierre en hora de Madrid, número de pujadores, clasificación en directo y la tarjeta de puja (desactivada hasta la fase 4, con aviso "Las pujas se abren muy pronto"). Las reglas se recuerdan junto al botón |
| `/entrar` | Formulario de acceso. Tras entrar vuelve a la página de la que venías (`?next=`, solo rutas internas para evitar redirecciones a webs externas) |
| `/registro` | Nombre, email, contraseña (con ayuda de "te faltan N caracteres") y casilla de aceptación que **explica la regla del 50 %** antes de crear la cuenta. El botón no se activa hasta marcarla |
| `/panel` | Zona privada. Saludo, pestañas (Mi anuncio · Saldo · Mis pujas · Avisos; las tres últimas marcadas "Pronto") y el **editor del anuncio** con subida de imagen (clic o arrastrar), contadores de caracteres, errores por campo y **vista previa en directo** idéntica a la portada |
| `/legal/terminos`, `/legal/privacidad` | Borradores claros (subasta, dinero, contenidos prohibidos, datos, cookies), marcados como provisionales hasta la fase 9 |
| cualquier otra | Página 404 amable |

### 7.3 Cómo gestiona la sesión la web
Implementa lo que diseñamos en la fase 2 (`lib/api.ts` y `lib/auth-context.tsx`):
1. El **access token** vive solo en memoria (una variable de JavaScript), nunca en `localStorage`.
2. Al abrir cualquier página, `AuthProvider` llama a `/api/auth/refresh`: si tienes la cookie, sigues dentro sin volver a escribir la contraseña.
3. Si una llamada responde 401 porque el token caducó, la web **renueva la sesión y repite la llamada** sin que lo notes. Si el token va a caducar en menos de 30 s, se renueva antes.
4. Si varias partes de la web piden renovar a la vez, se hace **una sola** petición (evita las "carreras" que detecta el backend). Si aun así el backend responde `REFRESH_TOKEN_RACE`, espera 300 ms y reintenta.
5. Si la sesión deja de ser válida, toda la web se entera (la cabecera cambia a "Entrar") y las páginas privadas te llevan a `/entrar`.

### 7.4 Datos "casi en directo"
La portada y la subasta piden `/api/public/home` **cada 15 segundos**. Los contadores se actualizan cada segundo en el navegador, corrigiendo el desfase con la hora del servidor. En la fase 4, el ranking y las extensiones del anti-sniping llegarán **al instante** por WebSocket.

## 8. Conceptos que necesitas entender

- **Next.js App Router:** cada carpeta dentro de `src/app` con un `page.tsx` es una página. `app/subasta/page.tsx` es `/subasta`. `layout.tsx` es el "marco" común (cabecera y pie).
- **Componentes de servidor y de cliente:** por defecto, los componentes de Next.js se generan en el servidor. Los que necesitan interactividad (botones, formularios, contadores) empiezan con `"use client"`. Las páginas son finas (título y metadatos) y delegan en un componente cliente.
- **Por qué los datos se piden desde el navegador:** si la portada pidiera los datos desde el servidor de Next.js, todas las visitas saldrían desde la misma IP (la de Vercel) y agotarían el límite de peticiones por IP del backend. Además, la compilación dependería de que el backend estuviera encendido. Pidiéndolos desde el navegador de cada visitante, cada uno cuenta por separado y la web se compila sin depender de la API.
- **Tailwind CSS:** los estilos se escriben como clases (`rounded-full bg-brand px-4`). Los colores y tipografías de marca están definidos una sola vez en `globals.css` (`@theme`) y se usan como `bg-brand`, `text-ink`, `font-display`…
- **`next/image` con `unoptimized`:** nuestro backend ya entrega las imágenes optimizadas, así que Next.js las muestra tal cual.
- **React Context (`AuthProvider`):** una forma de compartir "quién ha iniciado sesión" con toda la web sin pasarlo de componente en componente.
- **Cabeceras de seguridad de la web:**
  - `X-Frame-Options: DENY`: nadie puede meter Publifi en un iframe.
  - `nosniff`, `Referrer-Policy` y `Permissions-Policy`.
  - La política CSP completa llega en la fase 9.

## 9. Archivo por archivo

### 9.1 Raíz
| Archivo | Qué es |
|---|---|
| `dev.sh` | **Nuevo.** Arranca base de datos + API + web con un comando y lo para todo con Ctrl + C. Comprueba requisitos y puertos, y abre el navegador (`NO_BROWSER=1` para no abrirlo). Puertos configurables con `BACKEND_PORT` y `FRONTEND_PORT` |
| `docker-compose.yml` | Mod.: el backend en Docker usa el 8081 |
| `README.md`, `docs/ARCHITECTURE.md` | Mod.: Spring Boot 4, estructura del frontend, cómo arrancar |

### 9.2 Backend
| Archivo | Nuevo/Mod. | Qué hace |
|---|---|---|
| `pom.xml` | Mod. | Spring Boot 4.1.1, starters nuevos, springdoc 3.1.1, TwelveMonkeys (WebP/CMYK) |
| `application.yml` | Mod. | Sin `hibernate.jdbc.time_zone`; subida máxima de 5 MB; reglas de rate limiting nuevas |
| `application-dev.yml` | Mod. | Puerto 8081; datos de ejemplo activados (`DEMO_DATA`) |
| `common/config/AppProperties.java` | Mod. | Las reglas de rate limiting admiten `excludePathPrefixes` |
| `common/error/FieldValidationException.java` | Nuevo | Error de validación con mensaje por campo desde un servicio |
| `common/error/GlobalExceptionHandler.java` | Mod. | Devuelve `errors` para `FieldValidationException`; 413 `FILE_TOO_LARGE` |
| `common/error/ProblemResponseWriter.java` | Mod. | Jackson 3 (`JsonMapper`) + mixin de `ProblemDetail` |
| `common/text/TextSanitizer.java` | Nuevo | Limpieza de textos de una línea y multilínea |
| `common/text/WebsiteUrlSanitizer.java` | Nuevo | Validación y normalización de la web del anunciante |
| `common/web/RootController.java` | Nuevo | `GET /` → redirige a la web |
| `security/SecurityConfig.java` | Mod. | `GET /` público |
| `image/service/ImageProcessor.java` | Nuevo | Saneado de imágenes (ver §5.1) |
| `image/service/ImageService.java` | Nuevo | Guarda (subidas o generadas) y lee imágenes; `publicUrl()` |
| `image/service/ProcessedImage.java` | Nuevo | Record con la imagen ya procesada |
| `image/repository/StoredImageRepository.java` | Nuevo | Incluye `existsByIdAndOwnerId` (comprueba la propiedad sin cargar los bytes) |
| `image/dto/ImageResponse.java` | Nuevo | Respuesta de la subida |
| `image/controller/ImageController.java` | Nuevo | Subida y descarga (caché, ETag, 304) |
| `adprofile/dto/AdProfileRequest.java`, `AdProfileResponse.java` | Nuevos | Entrada (con validaciones en español) y salida |
| `adprofile/service/AdProfileService.java` | Nuevo | Leer y crear o actualizar el anuncio con todas las validaciones |
| `adprofile/repository/AdProfileRepository.java` | Nuevo | `findByUserId` |
| `adprofile/controller/AdProfileController.java` | Nuevo | `/api/me/ad-profile` |
| `home/dto/HomeResponse.java` | Nuevo | Estado, anuncio actual, subasta y ranking |
| `home/service/HomeService.java` | Nuevo | Decide qué se ve en la portada |
| `home/controller/PublicHomeController.java` | Nuevo | `/api/public/home` (sin caché) |
| `auction/repository/*` | Nuevos | `AuctionRepository`, `AuctionParticipationRepository` (ranking con desempate), `RankingRow`, `BidRepository` (`nextval('bid_seq')`) |
| `adslot/repository/AdSlotRepository.java` | Nuevo | `findLive` (anuncio aprobado en ventana) y consulta de pendientes |
| `settings/repository/AppSettingsRepository.java` | Nuevo | `getSettings()` |
| `wallet/repository/*` | Nuevos | Cuentas y transacciones del ledger |
| `demo/DemoDataSeeder.java` | Nuevo | Escenario de ejemplo coherente (solo en dev) |
| `demo/DemoImages.java` | Nuevo | Dibuja las imágenes de ejemplo (sin fuentes de texto, para que funcione también en Docker) |
| `user/service/AdminBootstrap.java` | Mod. | `@Order(0)`: el admin se crea antes que los datos de ejemplo |

### 9.3 Frontend (`frontend/`)
| Archivo | Qué es |
|---|---|
| `next.config.ts` | Sin cabecera `X-Powered-By`, imágenes sin reprocesar, cabeceras de seguridad |
| `.env.example` | `NEXT_PUBLIC_API_URL` para producción |
| `src/app/globals.css` | Sistema de diseño: colores, tipografías, sombras, animaciones y foco visible |
| `src/app/layout.tsx` | Tipografías, metadatos (título por página), cabecera, pie y `AuthProvider` |
| `src/app/page.tsx` | Portada |
| `src/app/subasta/page.tsx` | Subasta de hoy |
| `src/app/entrar/page.tsx`, `registro/page.tsx` | Formularios (con `Suspense`, obligatorio al leer `?next=`) |
| `src/app/panel/layout.tsx`, `page.tsx` | Zona privada protegida + editor del anuncio |
| `src/app/legal/terminos/page.tsx`, `privacidad/page.tsx` | Borradores legales |
| `src/app/not-found.tsx`, `icon.svg` | 404 y favicon |
| `src/lib/api.ts` | Cliente de la API: errores tipados (`ApiError` con `code` y `fieldErrors`), sesión en memoria, renovación automática y deduplicada |
| `src/lib/auth-context.tsx` | `AuthProvider` y `useAuth()` |
| `src/lib/hooks.ts` | `useHomeData()` (cada 15 s + desfase de reloj) y `useCountdown()` |
| `src/lib/format.ts` | Euros, tiempo restante, partes del contador, hora de Madrid, URL bonita |
| `src/lib/types.ts` | Tipos de las respuestas del backend |
| `src/lib/config.ts` | Dirección de la API (`NEXT_PUBLIC_API_URL`, por defecto `http://localhost:8081`) |
| `src/lib/navigation.ts` | `safeNextPath` (anti redirección abierta) |
| `src/lib/cn.ts` | Unir clases CSS |
| `src/components/ui/*` | `Button`/`ButtonLink`, `TextField`/`TextArea` (etiqueta, ayuda, error, contador), `Alert`, `Spinner`/`PageSpinner` |
| `src/components/layout/SiteHeader.tsx` | Cabecera fija con menú de móvil y estado de sesión |
| `src/components/layout/SiteFooter.tsx` | Pie con enlaces legales |
| `src/components/ad/AdView.tsx` | La página de anuncio (pantalla completa o vista previa) |
| `src/components/home/*` | `HomeClient` (orquesta la portada), `LiveDropdown`, `BaseHero`, `HowItWorks` |
| `src/components/auction/*` | `AuctionClient` (página de subasta), `RankingList` |
| `src/components/auth/*` | `AuthCard`, `LoginForm`, `RegisterForm`, `RequireAuth` |
| `src/components/panel/*` | `PanelShell`, `AdProfileEditor`, `ImageUploader` |
| `src/components/legal/LegalPage.tsx` | Maquetación de textos legales |
| `src/components/Logo.tsx`, `icons.tsx` | Logotipo e iconos SVG propios (sin dependencias) |
| *Borrados* | `favicon.ico` y los SVG de ejemplo de la plantilla |

### 9.4 Tests nuevos (backend)
| Archivo | Tests |
|---|---:|
| `common/text/WebsiteUrlSanitizerTest.java` | 18 |
| `common/text/TextSanitizerTest.java` | 5 |
| `image/service/ImageProcessorTest.java` | 8 |
| `adprofile/AdProfileIntegrationTest.java` | 9 |
| `home/HomeIntegrationTest.java` | 4 |
| `demo/DemoDataSeederTest.java` | 1 |
| `schema/EntityMappingTest.java` (+1: hora de cierre) | 2 |
| `support/TestImages.java` | utilidad: genera imágenes en memoria |

## 10. Configuración nueva

| Variable | Dónde | Por defecto | Para qué |
|---|---|---|---|
| `DEMO_DATA` | Backend (solo perfil dev) | `true` | Crear los datos de ejemplo si la base de datos está vacía |
| `NEXT_PUBLIC_API_URL` | Frontend | `http://localhost:8081` | Dirección de la API. En Vercel: `https://api.publifi.com` |
| `BACKEND_PORT`, `FRONTEND_PORT` | `dev.sh` | 8081 / 3000 | Cambiar puertos locales |
| `NO_BROWSER` | `dev.sh` | vacío | `1` = no abrir el navegador |

## 11. Tests y verificación

**Backend: 88 tests, 0 fallos.**

| Clase | Tests | Qué comprueba |
|---|---:|---|
| `WebsiteUrlSanitizerTest` | 18 | Añade `https://`; normaliza el dominio; punycode; puerto 443. Rechaza `http`, `javascript:`, `ftp`, `localhost`, IPs privadas y públicas, IPv6, usuario:clave, sin extensión, `.local`, espacios, guiones mal puestos, vacío y URLs de más de 2048 caracteres |
| `TextSanitizerTest` | 5 | Espacios y controles; caracteres de inversión de texto; emojis compuestos; párrafos; Unicode |
| `ImageProcessorTest` | 8 | PNG opaco → JPEG; logo transparente → PNG; JPEG aceptado; escalado a 1600 px manteniendo proporción; nunca se guarda el original; rechaza no-imágenes (incluido SVG con script), imágenes diminutas y dimensiones gigantes antes de decodificarlas |
| `AdProfileIntegrationTest` | 9 | Subida → imagen pública re-codificada con caché de un año y 304; rechazo de no-imágenes; subir exige sesión; 404 antes de crear el perfil; crear y actualizar (con limpieza de espacios y `https://` automático); webs peligrosas rechazadas campo a campo; no puedes usar la imagen de otro; campos obligatorios; descripción hecha de espacios |
| `HomeIntegrationTest` | 4 | Sin subastas → `NO_AD`; ganador aprobado → `AD` + ranking con desempate correcto; día vacío → `NO_BIDS`; pendiente → `PENDING_REVIEW` |
| `DemoDataSeederTest` | 1 | Escenario completo: portada, ranking, ledger cuadrado, ingresos 80 €, reservado 95 € y no se duplica al repetirse |
| Fases 1 y 2 | 43 | Siguen pasando en Spring Boot 4 (+1 test de la hora de cierre) |

**Frontend:** `npm run lint` sin avisos, `tsc` sin errores y `npm run build` correcto (todas las páginas se generan).

**Verificación manual y visual:**
- Arranqué backend y web reales y tomé capturas automáticas con Chrome:
  - portada en escritorio y móvil;
  - desplegable "En directo" abierto;
  - página de subasta;
  - registro;
  - panel tras iniciar sesión como `demo-cafe` y `demo-lumen`, en escritorio y móvil.

  En todas, **0 px de desbordamiento horizontal**.
- La imagen Docker del backend se construye con Spring Boot 4 y, dentro del contenedor, genera los datos de ejemplo y responde a la portada.
- `./dev.sh` probado de principio a fin: arranca todo, muestra el resumen y, al pararlo, libera todos los puertos.

## 12. Limitaciones conocidas y pendientes

| Tema | Situación | Cuándo |
|---|---|---|
| Pujar | La tarjeta de puja está desactivada | Fase 4 |
| Tiempo real | Actualización cada 15 s (no al instante) | Fase 4 (WebSocket) |
| Saldo, Mis pujas, Avisos | Pestañas marcadas "Pronto" | Fases 4, 5 y 7 |
| Historial de ganadores | Aún no existe la página | Fase 8/9 |
| Imágenes huérfanas (subidas y no usadas) | Se acumulan en la BD | Limpieza programada en la fase 6 |
| Imágenes en PostgreSQL | Correcto para empezar | Moverlas a R2/S3 si el tráfico crece |
| SEO de la portada | Los datos se cargan en el navegador (ver §8) | Metadatos por anuncio en la fase 9, si interesa |
| Textos legales | Borradores | Fase 9 + revisión profesional |
| Botón "N" abajo a la izquierda | Es el indicador de desarrollo de Next.js: **solo aparece en local**, nunca en producción | — |
| Recuperar contraseña y verificar email | Pendiente | Fase 7 |

## 13. Siguiente fase

**Fase 4 — Pujas, ranking en tiempo real, contador y anti-sniping:**
- Creación automática de la subasta del día.
- Endpoint de puja con bloqueo `FOR UPDATE`, idempotencia, mínimos, reserva de saldo en el ledger y anti-sniping.
- WebSocket STOMP para el ranking, el contador y el aviso "te han superado" en directo.
- Activar la tarjeta de puja con "Incrementar puja".
- Tests de pujas simultáneas, empates y anti-sniping.

Como el saldo solo se puede recargar en la fase 5, en la fase 4 añadiré una forma de darte saldo de prueba en local para poder pujar.
