# Fase 2 — Configuración del backend, Docker y autenticación

> **Estado:** ✅ terminada (2026-09-26)
> **Resultado verificable:**
> - `cd backend && ./mvnw test` → **42 tests, 0 fallos**.
> - `docker compose --profile full up -d --build` → backend y PostgreSQL en marcha; registro, login, renovación de sesión, `/api/me` y Swagger comprobados con peticiones reales.

---

## Índice

1. [Objetivo y resumen](#1-objetivo-y-resumen)
2. [Cambio de pagos: adiós Stripe, hola transferencia](#2-cambio-de-pagos-adiós-stripe-hola-transferencia)
3. [Aviso: soporte de Spring Boot 3.5](#3-aviso-soporte-de-spring-boot-35)
4. [Conceptos que necesitas entender](#4-conceptos-que-necesitas-entender)
5. [Cómo arrancarlo en tu ordenador, paso a paso](#5-cómo-arrancarlo-en-tu-ordenador-paso-a-paso)
6. [La API de esta fase](#6-la-api-de-esta-fase)
7. [Cómo usará el frontend la autenticación](#7-cómo-usará-el-frontend-la-autenticación)
8. [Archivo por archivo](#8-archivo-por-archivo)
9. [Todas las variables de configuración](#9-todas-las-variables-de-configuración)
10. [Decisiones de seguridad (y por qué)](#10-decisiones-de-seguridad-y-por-qué)
11. [Tests](#11-tests)
12. [Limitaciones conocidas y pendientes](#12-limitaciones-conocidas-y-pendientes)
13. [Siguiente fase](#13-siguiente-fase)

---

## 1. Objetivo y resumen

Dejar el backend listo para construir encima:

- **Docker:** PostgreSQL local con un comando e imagen de producción del backend.
- **Configuración** por perfiles (`dev`, `test`, producción) y variables de entorno, validada al arrancar.
- **Errores** uniformes en toda la API (RFC 9457) con códigos estables y mensajes en español.
- **Autenticación:** registro, login, JWT de acceso de 15 min, sesión renovable de 30 días con rotación y detección de robo, logout, roles USER/ADMIN y creación automática de tu cuenta de administrador.
- **Protecciones:** CORS, rate limiting por IP y cabeceras de seguridad.
- **Documentación** interactiva de la API con Swagger.
- **Salud** del servicio (`/actuator/health`) para Docker y Railway.

Además, a petición tuya, **se cambió el sistema de pagos** (sección 2).

## 2. Cambio de pagos: adiós Stripe, hola transferencia

### 2.1 Lo que pediste
Una plataforma **gratuita** en la que cada usuario pueda pagarte un **importe libre** y que "no dé problemas".

### 2.2 La respuesta honesta
1. **No existe ninguna pasarela de pago con tarjeta que sea gratis.** Todas (Stripe, PayPal, Mollie, SumUp, el TPV virtual de tu banco/Redsys…) cobran una comisión por pago, porque las redes de tarjetas y los bancos cobran a su vez. Suele rondar entre el 1 % y el 3 % más unos céntimos por operación, según proveedor y tarjeta. Consulta las tarifas actuales de cada una.
2. **La única vía realmente gratuita es la transferencia bancaria SEPA a tu cuenta.** Recibir transferencias en euros suele ser gratis, y el importe lo decide quien paga. Además, según el Reglamento (UE) 2024/886, desde octubre de 2025 los bancos de la zona euro deben ofrecer la **transferencia inmediata** (llega en segundos) al mismo precio que la normal, que en la mayoría de bancos españoles es 0 €.
3. **Cambiar de proveedor no quita el "problema".** El riesgo no está en Stripe sino en el modelo: que los perdedores pierdan el 50 % puede considerarse juego o una cláusula abusiva. Todas las pasarelas tienen normas parecidas sobre juego, y tu banco también puede preguntar por muchos ingresos pequeños de particulares. Con transferencia no hay un intermediario que te pueda congelar los fondos, pero **el riesgo legal sigue ahí** y hay que consultarlo con un abogado (ARCHITECTURE §11).
4. **Bizum:** el Bizum entre particulares no está pensado para cobrar por un negocio. El Bizum para comercios se contrata con tu banco (como un TPV) y tiene comisión.

### 2.3 Comparativa

| Opción | Coste para ti | Importe libre | ¿Automático? | Comentario |
|---|---|---|---|---|
| **Transferencia SEPA (inmediata) a tu IBAN** | Normalmente 0 € | ✅ | Semiautomático: confirmas tú o importas el extracto | ✅ **Elegida** |
| Bizum para comercios (vía tu banco) | Comisión del banco | ✅ | ✅ | Requiere contrato con el banco |
| Pasarelas de tarjeta (Stripe, PayPal, Mollie, Redsys…) | ~1–3 % + céntimos por pago | ✅ | ✅ | Ninguna es gratis; normas sobre juego similares |
| Bizum entre particulares a tu móvil | 0 € | ✅ | ❌ | No es para cobros de un negocio: **descartado** |

### 2.4 Cómo funcionará la recarga por transferencia (se programa en la fase 5)
1. El usuario escribe cuánto quiere recargar (importe libre) y pulsa "Recargar".
2. Publifi crea una recarga `PENDING` con un **código único** (p. ej. `PUB-7K3Q9D`) y le muestra: tu IBAN, el titular, el importe y el código que debe poner **en el concepto**.
3. El usuario hace la transferencia inmediata desde su banco.
4. Cuando el dinero llega:
   - **Modo manual:** en el panel de admin ves las recargas pendientes y pulsas "Confirmar" introduciendo el importe recibido.
   - **Modo semiautomático:** descargas el extracto de tu banco (formato **Norma 43**, que ofrecen todos los bancos españoles, o CSV), lo subes al panel y el sistema casa cada código con su recarga. Cada movimiento se registra en `payment_events`, así que subir el mismo extracto dos veces no abona nada dos veces.
5. Se abona **lo realmente recibido** en el monedero (transacción `TOP_UP` del ledger) y el usuario recibe una notificación.
6. Si no llega nada en X días (configurable), la recarga caduca.

**Consecuencias que debes conocer:**
- Una recarga no es instantánea en Publifi hasta que la confirmas. Los usuarios deberán recargar **con antelación**, no en el último minuto de la subasta. Lo explicaremos en la interfaz.
- Hacerlo 100 % automático sin intervención requiere una API bancaria (open banking), que normalmente es de pago. El diseño lo permite más adelante.
- Cobra en una **cuenta de empresa o de autónomo** a nombre del titular de Publifi, no en tu cuenta personal.

### 2.5 Qué se cambió en el código

| Antes (Stripe) | Ahora |
|---|---|
| Cuenta contable `STRIPE_CLEARING` | `PAYMENTS_CLEARING` (contrapartida de todo dinero que entra de fuera) |
| `top_ups` con `stripe_checkout_session_id` y `stripe_payment_intent_id` | `top_ups` con `method` (`BANK_TRANSFER`/`CARD`), `reference_code` único, `requested_cents`, `received_cents`, `provider`, `provider_payment_id`, `confirmed_by` (admin), `expires_at`; nuevo estado `CANCELLED` |
| Tabla `stripe_events` (id del evento de Stripe) | `payment_events` con `(provider, event_id)` único: sirve para líneas de extracto bancario y para una futura pasarela |
| `StripeEvent.java` | Borrado → `PaymentEvent.java` |
| `TopUp.java` (Stripe) | Reescrito; nuevo `TopUpMethod.java`; `TopUpStatus` con `CANCELLED` |

Se modificaron las migraciones V5 y V6 en lugar de crear una V9. **Solo era seguro porque la base de datos aún no existía en ningún sitio real.** A partir de ahora, que ya se ha arrancado la base de datos local, cualquier cambio irá en migraciones nuevas.

## 3. Aviso: soporte de Spring Boot 3.5

> **Actualización:** resuelto en la fase 3. El proyecto se migró a **Spring Boot 4.1.1** ([FASE-03 §3](FASE-03.md#3-migración-a-spring-boot-4)). Los puertos locales también cambiaron: la API usa el 8081 por defecto y todo se arranca con `./dev.sh`.

Pediste Spring Boot 3 y estamos en la **3.5.16**, la última de esa rama. Pero el soporte gratuito (*open source*) de la rama 3.5 **terminó el 30/06/2026**: a partir de ahí los parches de seguridad son de pago. Para aprender y desarrollar no pasa nada. **Antes de lanzar a producción te recomiendo migrar a Spring Boot 4.** Cuanto antes, menos código hay que tocar. Si me dices que sí, lo hago al principio de la fase 3.

## 4. Conceptos que necesitas entender

### 4.1 JWT (access token)
Un **JWT** es un texto con tres partes separadas por puntos: cabecera, datos (*claims*) y firma. Ejemplo decodificado de nuestros datos:
```json
{ "iss": "publifi", "sub": "f5d06817-…", "roles": ["USER"], "iat": 1790420278, "exp": 1790421178 }
```
- El servidor lo **firma** con un secreto (`JWT_SECRET`, algoritmo HS256). Si alguien cambia un solo carácter (por ejemplo, `USER` por `ADMIN`), la firma deja de cuadrar y se rechaza.
- **No está cifrado:** cualquiera puede leerlo (prueba a pegarlo en jwt.io). Por eso solo lleva el id y el rol, nunca datos personales.
- El servidor **no guarda sesiones**: con verificar la firma y la caducidad sabe quién eres. Esto es lo que se llama *stateless*.
- Dura **15 minutos**. Si te lo roban, sirve poco tiempo.

### 4.2 Refresh token (la sesión larga)
Como el access token caduca a los 15 minutos, hace falta otro para pedir uno nuevo sin volver a escribir la contraseña:
- Es un texto aleatorio de 256 bits que dura **30 días**.
- Viaja en una **cookie** con estos atributos:
  - `HttpOnly`: JavaScript no puede leerla, así que un fallo XSS no la roba.
  - `Secure`: solo viaja por HTTPS.
  - `SameSite=Lax`: el navegador no la envía en peticiones que otras webs lancen contra tu API.
  - `Path=/api/auth`: solo viaja a las rutas de autenticación.
- En la base de datos solo guardamos su **hash SHA-256**. Si alguien robara la base de datos, no podría usarlos.

### 4.3 Rotación y detección de robo
Cada vez que se usa el refresh token, el servidor lo **revoca** y entrega uno nuevo de la misma "familia". Si alguien vuelve a presentar uno ya revocado, alguien tiene una copia: el servidor **revoca toda la familia** y ambos (ladrón y víctima) tienen que volver a entrar.

Caso especial: dos pestañas renuevan a la vez con el mismo token. Si el token se revocó hace menos de 10 segundos, se responde `REFRESH_TOKEN_RACE` sin revocar nada. El frontend reintenta y el navegador ya tiene la cookie nueva.

### 4.4 CORS
Por seguridad, el navegador no deja que la web A lea respuestas de la API B salvo que B lo autorice. Nuestra API autoriza **solo** los orígenes de `FRONTEND_ORIGINS` (en local `http://localhost:3000`), con cookies (`allowCredentials`). Si otra web lo intenta, el backend responde 403.

### 4.5 CSRF
Es el ataque en el que una web maliciosa hace que tu navegador envíe peticiones a otra web donde tienes sesión. Aquí no aplica a casi nada, porque las rutas protegidas exigen la cabecera `Authorization`, que otra web no puede añadir. Las dos rutas que usan cookie (`/refresh` y `/logout`) están protegidas por `SameSite=Lax` y por CORS. Por eso la protección CSRF clásica de Spring está desactivada.

### 4.6 Rate limiting (token bucket)
Cada IP tiene un "cubo" de fichas por regla: por ejemplo, 10 intentos de login por minuto. Cada petición gasta una ficha y el cubo se rellena con el tiempo. Sin fichas, la respuesta es **429 Too Many Requests** con la cabecera `Retry-After`. Frena ataques de fuerza bruta contra contraseñas y el spam de registros.

### 4.7 Formato de errores (RFC 9457)
Todos los errores tienen la misma forma. El frontend usa `code` (que no cambia) y muestra `detail` al usuario:
```json
{
  "type": "about:blank",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Necesitas iniciar sesión.",
  "instance": "/api/me",
  "code": "UNAUTHORIZED",
  "timestamp": "2026-09-26T10:57:59Z"
}
```
Los errores de validación añaden `errors` con el mensaje de cada campo.

### 4.8 Perfiles de Spring y variables de entorno
- `application.yml` es la configuración base, pensada para producción. Los secretos llegan por **variables de entorno**, como `${JWT_SECRET}`.
- `application-dev.yml` se activa con `SPRING_PROFILES_ACTIVE=dev`. Trae un secreto de desarrollo, cookies sin `Secure` (porque en local no hay HTTPS), Swagger activado y un admin de prueba.
- `application-test.yml` (en `src/test`) se usa en los tests.
- **`JWT_SECRET` no tiene valor por defecto a propósito:** si olvidas ponerlo en producción, la aplicación no arranca, en vez de arrancar con un secreto conocido.

### 4.9 Docker: imagen, contenedor, volumen, Compose
- **Imagen:** una "plantilla" con todo lo necesario para ejecutar algo (sistema base + Java + tu aplicación).
- **Contenedor:** una imagen en ejecución.
- **Volumen:** disco persistente de un contenedor. Los datos de PostgreSQL sobreviven aunque borres el contenedor.
- **Compose:** un archivo (`docker-compose.yml`) que arranca varios contenedores juntos con un comando.
- **Build multi-etapa:** nuestro `Dockerfile` compila en una imagen grande (con Maven y el JDK) y copia solo el resultado a una imagen pequeña (solo el JRE). La imagen final es más ligera y segura.

### 4.10 Actuator y Swagger
- `/actuator/health` responde `{"status":"UP"}` si la aplicación y la base de datos funcionan. Railway lo usará para saber si el despliegue arrancó bien.
- **Swagger UI** (`/swagger-ui.html`) es una web generada automáticamente con todos los endpoints, que permite probarlos desde el navegador. Está activado en `dev` y desactivado en producción.

### 4.11 Maven Wrapper (`mvnw`)
Es un script que descarga y usa la versión exacta de Maven del proyecto (3.9.16). Así cualquiera, incluido un servidor, compila igual sin tener Maven instalado: `./mvnw test` en lugar de `mvn test`.

## 5. Cómo arrancarlo en tu ordenador, paso a paso

**Requisitos:** Docker Desktop abierto y Java 21.

> ⚠️ **Puerto 8080 ocupado:** en tu ordenador el puerto 8080 lo está usando tu proyecto `voicebot_hosteleria`. Por eso los ejemplos usan el **8081**. Si cierras el voicebot, puedes usar el 8080.

### Opción A: base de datos en Docker, backend con Maven (recomendada para programar)

```bash
# 1. Desde la raíz del proyecto: arranca PostgreSQL
docker compose up -d

# 2. Arranca el backend con el perfil de desarrollo
cd backend
PORT=8081 SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```
Cuando veas `Started PublifiApplication`, el backend está listo.
- API: http://localhost:8081
- Swagger: http://localhost:8081/swagger-ui.html
- Admin de desarrollo: `admin@publifi.local` / `AdminPublifi2026!`

Para parar: `Ctrl + C` en la terminal del backend y `docker compose down` para PostgreSQL. Los datos se conservan.

### Opción B: todo en Docker (como en producción)

```bash
BACKEND_PORT=8081 docker compose --profile full up -d --build
docker compose logs -f backend      # ver los logs (Ctrl + C para salir)
docker compose --profile full down  # parar
```

### Borrar la base de datos local y empezar de cero
```bash
docker compose --profile full down -v
```

### Si el 5432 también estuviera ocupado
```bash
POSTGRES_PORT=5433 docker compose up -d
DB_URL=jdbc:postgresql://localhost:5433/publifi PORT=8081 SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

### Probar la API con Swagger
1. Abre http://localhost:8081/swagger-ui.html.
2. Abre `POST /api/auth/login`, pulsa **Try it out**, escribe el email y la contraseña del admin y pulsa **Execute**.
3. Copia el `accessToken` de la respuesta.
4. Pulsa **Authorize** (arriba), pega el token y confirma.
5. Prueba `GET /api/me`.

## 6. La API de esta fase

| Método | Ruta | Acceso | Qué hace | Respuesta OK |
|---|---|---|---|---|
| POST | `/api/auth/register` | Público | Crea cuenta y abre sesión | 201 + `AuthResponse` + cookie |
| POST | `/api/auth/login` | Público | Inicia sesión | 200 + `AuthResponse` + cookie |
| POST | `/api/auth/refresh` | Cookie | Access token nuevo y rota la cookie | 200 + `AuthResponse` + cookie nueva |
| POST | `/api/auth/logout` | Cookie | Revoca la sesión y borra la cookie | 204 |
| GET | `/api/me` | Sesión | Tus datos | 200 + `UserResponse` |
| GET | `/actuator/health` | Público | Estado del servicio | 200 `{"status":"UP"}` |
| GET | `/v3/api-docs`, `/swagger-ui.html` | Público (solo en dev) | Documentación | 200 |

**Registro:**
```bash
curl -i -X POST http://localhost:8081/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"ana@ejemplo.com","password":"una-contraseña-larga","displayName":"Ana","acceptTerms":true}'
```
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9…",
  "tokenType": "Bearer",
  "expiresAt": "2026-09-26T11:12:58Z",
  "expiresIn": 900,
  "user": { "id": "f5d0…", "email": "ana@ejemplo.com", "displayName": "Ana", "role": "USER", "createdAt": "…" }
}
```
Cabecera:
```
Set-Cookie: publifi_refresh=…; Path=/api/auth; Max-Age=2592000; HttpOnly; SameSite=Lax
```
En producción también lleva `Secure`.

**Reglas de validación del registro:**
- Email válido.
- Contraseña de 10 a 72 caracteres (y como máximo 72 bytes: ver §10).
- Nombre de 2 a 80 caracteres.
- `acceptTerms` debe ser `true`.

**Códigos de error:**

| HTTP | `code` | Cuándo |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Campos no válidos (detalle en `errors`) |
| 400 | `MALFORMED_REQUEST` | El cuerpo no es JSON válido |
| 400 | `PASSWORD_TOO_LONG` | Contraseña de más de 72 bytes |
| 401 | `INVALID_CREDENTIALS` | Email o contraseña incorrectos (el mismo mensaje en ambos casos) |
| 401 | `UNAUTHORIZED` | Ruta protegida sin token |
| 401 | `TOKEN_INVALID` | Token caducado o manipulado → el frontend debe llamar a `/refresh` |
| 401 | `NO_REFRESH_TOKEN` | `/refresh` sin cookie |
| 401 | `REFRESH_TOKEN_INVALID` / `REFRESH_TOKEN_EXPIRED` | Sesión inexistente o caducada → a "Entrar" |
| 401 | `REFRESH_TOKEN_REUSED` | Posible robo: sesión cerrada en todos los dispositivos |
| 401 | `REFRESH_TOKEN_RACE` | Dos pestañas renovando a la vez → reintentar una vez |
| 403 | `ACCESS_DENIED` | Rol insuficiente (p. ej. `/api/admin`) |
| 403 | `ACCOUNT_DISABLED` | Cuenta desactivada |
| 403 | *(texto "Invalid CORS request")* | CORS: origen no autorizado |
| 404 | `USER_NOT_FOUND` | El usuario del token ya no existe |
| 409 | `EMAIL_TAKEN` | Email ya registrado |
| 409 | `CONCURRENT_MODIFICATION` / `DATA_CONFLICT` | Choques de concurrencia o de datos |
| 422 | `INSUFFICIENT_FUNDS` | Saldo insuficiente (desde la fase 4) |
| 429 | `RATE_LIMITED` | Demasiadas peticiones (mira `Retry-After`) |
| 500 | `INTERNAL_ERROR` | Error inesperado (se registra en el log; al usuario nunca se le muestran detalles) |

**Límites por IP** (configurables):

| Ruta | Límite |
|---|---|
| Login | 10 por minuto |
| Registro | 5 por hora |
| Refresh | 30 por minuto |
| Cualquier `/api/*` | 300 por minuto |

## 7. Cómo usará el frontend la autenticación

Esto se programa en la fase 3, pero el backend ya está preparado:

```
Navegador (Next.js)                                   Backend
───────────────────                                   ───────
1. Login ──────── POST /api/auth/login ─────────────▶ valida la contraseña
   ◀──────────── { accessToken } + Set-Cookie ─────── (cookie HttpOnly)
2. Guarda accessToken EN MEMORIA (nunca en localStorage)
3. Cada llamada ─ Authorization: Bearer <token> ───▶ /api/…
4. Si responde 401 TOKEN_INVALID:
   POST /api/auth/refresh (credentials: 'include') ─▶ rota la cookie
   ◀──────────── { accessToken nuevo } ────────────── y repite la llamada original
5. Al recargar la página (se pierde la memoria):
   POST /api/auth/refresh ─────────────────────────▶ si la cookie es válida, sigues dentro
6. Logout ─────── POST /api/auth/logout ────────────▶ revoca la sesión y borra la cookie
```
Todas las llamadas a `/api/auth/*` desde el navegador deben usar `fetch(..., { credentials: 'include' })` para que viaje la cookie.

## 8. Archivo por archivo

### 8.1 Raíz del proyecto

| Archivo | Nuevo/Mod. | Qué es |
|---|---|---|
| `docker-compose.yml` | Nuevo | PostgreSQL 17 con volumen persistente y health check. Servicio `backend` opcional (perfil `full`). Puertos configurables (`POSTGRES_PORT`, `BACKEND_PORT`) |
| `.env.example` | Nuevo | Plantilla de las variables de entorno de producción, con explicación de cada una |
| `README.md` | Mod. | Comandos actualizados |
| `docs/ARCHITECTURE.md` | Mod. | Pagos por transferencia, aviso de Spring Boot, riesgos, estado de las fases |
| `docs/fases/FASE-01.md`, `FASE-02.md` | Nuevos | Estos documentos |

### 8.2 Backend: build y Docker

| Archivo | Nuevo/Mod. | Qué es |
|---|---|---|
| `backend/pom.xml` | Mod. | Añade: Spring Security, OAuth2 Resource Server (JWT con Nimbus), Actuator, springdoc-openapi 2.8.17 (Swagger), Bucket4j 8.20.0 (rate limiting), Caffeine (caché), configuration-processor y spring-security-test. `finalName` fijo `publifi-backend.jar` |
| `backend/Dockerfile` | Nuevo | Build multi-etapa: compila con `maven:3.9-eclipse-temurin-21`, extrae el JAR por capas y lo ejecuta en `eclipse-temurin:21-jre` con un usuario sin privilegios y límites de memoria adecuados para contenedores |
| `backend/.dockerignore` | Nuevo | Excluye `target/`, `.env`, etc. al construir la imagen |
| `backend/mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties` | Nuevos | Maven Wrapper (Maven 3.9.16) |

### 8.3 Backend: configuración (`src/main/resources`)

| Archivo | Qué es |
|---|---|
| `application.yml` | Mod. Configuración de producción: puerto `PORT`, cabeceras de proxy, apagado ordenado, pool de conexiones, errores RFC 9457, Actuator (solo health), Swagger desactivado por defecto y todas las propiedades `app.*` (ver §9) |
| `application-dev.yml` | Nuevo. Perfil de desarrollo: Swagger activado, secreto JWT de desarrollo, cookie sin `Secure`, admin de prueba y log DEBUG de `com.publifi` |

### 8.4 Backend: código nuevo o modificado (`src/main/java/com/publifi/`)

**Arranque**

| Archivo | Qué hace |
|---|---|
| `PublifiApplication.java` | Mod.: `@ConfigurationPropertiesScan` para cargar `AppProperties` |

**`common/config`**

| Archivo | Qué hace |
|---|---|
| `AppProperties.java` | Todas las propiedades `app.*` como *records* tipados y validados (`@NotBlank`, `@Positive`…). Si falta algo obligatorio, no arranca. Incluye la lógica de coincidencia de las reglas de rate limiting |
| `ClockConfig.java` | Bean `Clock` (UTC). Los servicios le piden la hora para que los tests puedan fijarla |
| `OpenApiConfig.java` | Título y descripción de la API en Swagger y esquema de seguridad "Bearer JWT" (botón Authorize) |

**`common/error`**

| Archivo | Qué hace |
|---|---|
| `ApiException.java` | Excepción de negocio con estado HTTP, `code` y mensaje. Métodos de fábrica: `badRequest`, `unauthorized`, `forbidden`, `notFound`, `conflict`, `unprocessable` |
| `Problems.java` | Construye respuestas RFC 9457 y les añade `code` y `timestamp` |
| `ProblemResponseWriter.java` | Escribe un error JSON directamente en la respuesta. Lo usan los filtros de seguridad y el rate limiting, que actúan antes de llegar a los controladores |
| `GlobalExceptionHandler.java` | `@RestControllerAdvice` que convierte cualquier excepción en una respuesta RFC 9457: errores de negocio, saldo insuficiente, concurrencia, integridad de datos, seguridad, validación (con mensaje por campo), JSON mal formado y errores inesperados (500 sin detalles internos, registrados en el log) |

**`security`**

| Archivo | Qué hace |
|---|---|
| `SecurityConfig.java` | Reglas de acceso por ruta, sesión sin estado, CSRF desactivado (ver §4.5), CORS, validación de JWT, conversión del claim `roles` en `ROLE_*`, ignorar `Authorization` en `/api/auth/**`, manejadores 401/403 en JSON, filtro de rate limiting después de CORS y `PasswordEncoder` (BCrypt con prefijo `{bcrypt}`) |
| `JwtConfig.java` | Clave HMAC a partir de `JWT_SECRET` (Base64 de al menos 32 bytes, o no arranca), codificador y decodificador de JWT que comprueban firma, caducidad y emisor (`iss = publifi`) |
| `TokenService.java` | Emite access tokens: `sub` = id de usuario, `roles`, `iat`, `exp` e `iss` |
| `AccessToken.java` | Record: el token y su caducidad |
| `CurrentUser.java` | `CurrentUser.id(jwt)`: el UUID del usuario autenticado en un controlador |
| `ProblemAuthenticationEntryPoint.java` | Respuestas 401: `UNAUTHORIZED` si falta el token y `TOKEN_INVALID` si está caducado o manipulado |
| `ProblemAccessDeniedHandler.java` | Respuestas 403 `ACCESS_DENIED` |
| `ratelimit/RateLimitFilter.java` | Token bucket por (regla, IP) con Bucket4j. Los cubos viven en Caffeine y caducan a las 2 h sin uso, con un máximo de 200 000. Responde 429 con `Retry-After` |

**`user/repository`**

| Archivo | Qué hace |
|---|---|
| `UserRepository.java` | `findByEmail`, `existsByEmail` |
| `RefreshTokenRepository.java` | `findByTokenHashForUpdate` (bloqueo `FOR UPDATE` + carga del usuario), `findByTokenHash`, `revokeFamily` |

**`user/service`**

| Archivo | Qué hace |
|---|---|
| `AuthService.java` | Registro (normaliza el email, comprueba los 72 bytes, detecta emails duplicados incluso en carrera), login (comparación de tiempo constante con email inexistente, cuenta desactivada, actualización automática del hash si cambia el algoritmo), refresh y logout |
| `RefreshTokenService.java` | Emisión (256 bits aleatorios, se guarda solo el SHA-256), rotación con bloqueo, detección de reutilización con margen de 10 s y revocación por familia. `noRollbackFor` asegura que la revocación se guarda aunque se lance un error |
| `AdminBootstrap.java` | Al arrancar, crea el admin de `ADMIN_EMAIL`/`ADMIN_PASSWORD` si no existe (mínimo 12 caracteres). Nunca asciende una cuenta existente |
| `AuthSession.java`, `IssuedRefreshToken.java` | Records con el resultado de abrir o renovar una sesión |

**`user/dto`** (lo que entra y sale por la API; las entidades nunca salen)

| Archivo | Qué es |
|---|---|
| `RegisterRequest.java` | Email, contraseña, nombre y aceptación de términos, con validaciones y mensajes en español |
| `LoginRequest.java` | Email y contraseña |
| `AuthResponse.java` | Access token, tipo, caducidad y usuario |
| `UserResponse.java` | Id, email, nombre, rol y fecha de alta (nunca el hash de la contraseña) |

**`user/controller`**

| Archivo | Qué hace |
|---|---|
| `AuthController.java` | `/api/auth/register`, `/login`, `/refresh` y `/logout`. Construye la cookie del refresh token y la borra cuando la sesión deja de valer |
| `MeController.java` | `GET /api/me` |

**Cambios de pagos** (ver §2.5): `payment/domain/TopUp.java`, `TopUpMethod.java` (nuevo), `TopUpStatus.java`, `PaymentEvent.java` (nuevo; sustituye a `StripeEvent.java`, borrado), `wallet/domain/LedgerAccountType.java` y `LedgerTransactionType.java`; migraciones `V5` y `V6`.

### 8.5 Backend: tests (`src/test`)

| Archivo | Nuevo/Mod. | Qué es |
|---|---|---|
| `resources/application-test.yml` | Nuevo | Perfil de test: secreto de test, cookie `Secure` (como en producción), margen de reutilización 0 s, admin de test y logs de Docker silenciados |
| `support/IntegrationTest.java` | Nuevo | Anotación que agrupa `@SpringBootTest`, MockMvc, perfil `test` y Testcontainers |
| `support/ApiTestSupport.java` | Nuevo | Utilidades: emails únicos, IP aleatoria por petición (para no chocar con el rate limiting), `register()`, `login()`, lectura de tokens y cookies |
| `support/TestAdminController.java` | Nuevo | Endpoint `/api/admin/_test/ping`, que **solo existe en los tests**, para probar el rol ADMIN |
| `user/AuthIntegrationTest.java` | Nuevo | 13 tests de extremo a extremo de la autenticación |
| `security/SecurityIntegrationTest.java` | Nuevo | 6 tests: CORS, rate limiting, health, OpenAPI y rutas protegidas |
| `user/service/RefreshTokenServiceTest.java` | Nuevo | 6 tests unitarios con Mockito y reloj fijo |
| `user/service/AuthServiceTest.java` | Nuevo | 2 tests unitarios con Mockito |
| `schema/*.java` | Mod. | Usan `@IntegrationTest`; adaptados al nuevo modelo de pagos |

## 9. Todas las variables de configuración

| Variable de entorno | Propiedad | Por defecto | Para qué |
|---|---|---|---|
| `PORT` | `server.port` | `8080` | Puerto HTTP (Railway lo inyecta) |
| `SPRING_PROFILES_ACTIVE` | — | *(ninguno)* | `dev` en local |
| `DB_URL` | `spring.datasource.url` | `jdbc:postgresql://localhost:5432/publifi` | Conexión JDBC |
| `DB_USERNAME` / `DB_PASSWORD` | `spring.datasource.*` | `publifi` / `publifi` | Credenciales de la base de datos |
| `DB_POOL_SIZE` | `spring.datasource.hikari.maximum-pool-size` | `10` | Conexiones simultáneas |
| `JWT_SECRET` | `app.jwt.secret` | **obligatorio** (dev/test traen el suyo) | Firma de los JWT. Genera uno con `openssl rand -base64 48` |
| `FRONTEND_ORIGINS` | `app.frontend-origins` | `http://localhost:3000` | Orígenes permitidos por CORS (separados por comas) |
| `REFRESH_COOKIE_SECURE` | `app.refresh-cookie.secure` | `true` (`false` en dev) | Cookie solo por HTTPS |
| `REFRESH_COOKIE_DOMAIN` | `app.refresh-cookie.domain` | vacío | Dominio de la cookie (vacío = el de la API) |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` / `ADMIN_NAME` | `app.admin.*` | vacío (dev: `admin@publifi.local` / `AdminPublifi2026!`) | Crear tu admin al arrancar |
| `SPRINGDOC_ENABLED` | `springdoc.*.enabled` | `false` (`true` en dev) | Activar Swagger |
| `RATE_LIMIT_ENABLED` | `app.rate-limit.enabled` | `true` | Activar el rate limiting |

Fijas en `application.yml`:
- `app.jwt.issuer=publifi`
- `access-token-ttl=15m`
- `refresh-token-ttl=30d`
- `refresh-cookie.name=publifi_refresh`
- `same-site=Lax`
- `reuse-grace=10s`
- `legal.terms-version="2026-09"`
- Reglas de rate limiting (ver §6)

## 10. Decisiones de seguridad (y por qué)

1. **Mismo mensaje y mismo tiempo** para "email no existe" y "contraseña incorrecta". Así nadie puede averiguar qué emails están registrados.
2. **BCrypt** con prefijo `{bcrypt}`: lento a propósito, lo que dificulta descifrar contraseñas por fuerza bruta. El prefijo permite cambiar de algoritmo sin romper los hashes antiguos.
3. **Límite de 72 bytes:** BCrypt ignora lo que pasa de 72 bytes. Una contraseña con muchas "ñ" o "€" puede superar ese límite con menos de 72 caracteres, así que se rechaza con un mensaje claro.
4. **Registro con email duplicado → 409 `EMAIL_TAKEN`.** Esto revela que el email existe. Es la práctica habitual por usabilidad y está limitado a 5 registros por hora e IP.
5. **El refresh token se guarda como hash, se rota y se revoca por familia** al detectar reutilización.
6. **El admin nunca se crea ascendiendo una cuenta existente:** así nadie se convierte en admin registrándose antes con tu email.
7. **`JWT_SECRET` obligatorio y de al menos 256 bits.**
8. **Swagger desactivado en producción** (no das a nadie el mapa de tu API).
9. **Actuator solo expone `health`**, sin detalles.
10. **El contenedor se ejecuta con un usuario sin privilegios.**
11. **Los errores 500 nunca muestran trazas ni mensajes internos.**

## 11. Tests

**42 tests, 0 fallos:**

| Clase | Tests | Tipo |
|---|---:|---|
| `AuctionDomainTest` | 7 | Unitario (fase 1) |
| `SchemaMigrationTest` | 7 | Integración con PostgreSQL (fase 1) |
| `EntityMappingTest` | 1 | Integración con PostgreSQL (fase 1, adaptado) |
| `AuthIntegrationTest` | 13 | Integración HTTP + PostgreSQL |
| `SecurityIntegrationTest` | 6 | Integración HTTP + PostgreSQL |
| `RefreshTokenServiceTest` | 6 | Unitario con Mockito |
| `AuthServiceTest` | 2 | Unitario con Mockito |

**`AuthIntegrationTest`:**
1. El registro crea el usuario, devuelve el token y la cookie tiene `HttpOnly`, `Secure`, `SameSite=Lax` y `Path=/api/auth`. La contraseña se guarda con BCrypt y la versión de términos queda registrada.
2. Rechaza emails duplicados aunque cambien mayúsculas y minúsculas.
3. Valida todos los campos con un mensaje por campo.
4. Rechaza contraseñas de más de 72 bytes.
5. Un JSON mal formado da `MALFORMED_REQUEST`.
6. El login funciona con cualquier combinación de mayúsculas en el email.
7. El login falla con el mismo código para "contraseña incorrecta" y "email inexistente".
8. `/api/me`: sin token da `UNAUTHORIZED`, con un token inventado da `TOKEN_INVALID`, y con uno válido devuelve los datos sin el hash.
9. Un token caducado da `TOKEN_INVALID`.
10. `/api/admin/**` responde 403 a un USER y 200 al ADMIN.
11. El refresh rota el token; reutilizar el viejo da `REFRESH_TOKEN_REUSED`, borra la cookie e invalida también el nuevo.
12. Refresh sin cookie o con un token inventado.
13. El logout revoca la sesión.

**`SecurityIntegrationTest`:**
1. El preflight CORS admite el frontend con credenciales.
2. CORS rechaza orígenes desconocidos.
3. El intento número 11 de login desde la misma IP recibe 429 con `Retry-After`; otra IP no se ve afectada.
4. `/actuator/health` es público.
5. El documento OpenAPI se publica.
6. Las rutas desconocidas exigen sesión.

**`RefreshTokenServiceTest`:**
1. La rotación revoca el token viejo y mantiene la familia.
2. Una reutilización dentro de los 10 s se trata como carrera (no revoca).
3. Una reutilización fuera de los 10 s revoca la familia.
4. Un token caducado se rechaza.
5. Un token desconocido se rechaza.
6. Solo se guarda el hash.

**`AuthServiceTest`:**
1. Con un email inexistente se comprueba igualmente una contraseña ficticia.
2. Una cuenta desactivada no puede entrar.

**Verificación manual realizada** con `docker compose --profile full up` y `curl` contra la aplicación real:
- Las 8 migraciones se aplicaron en PostgreSQL y se creó el admin.
- `/actuator/health` → `UP`.
- Registro → 201 con token y cookie `HttpOnly` en `/api/auth`.
- Refresh con la cookie → 200.
- Login del admin y `/api/me` → `role: ADMIN`.
- `/api/me` sin token → error RFC 9457 en español.
- Swagger UI → 200.

## 12. Limitaciones conocidas y pendientes

| Tema | Situación | Cuándo |
|---|---|---|
| **Recuperar contraseña** ("he olvidado mi contraseña") | No existe todavía: necesita el envío de emails | Fase 7 (lo añadiré aunque no estaba en tu lista: es imprescindible) |
| Verificación del email al registrarse | No se exige. La columna `email_verified_at` ya existe | Fase 7 (recomendado) |
| Limpieza de refresh tokens caducados | Se acumulan en la tabla | Tarea programada en la fase 6 (con ShedLock) |
| Rate limiting con varias instancias | Los contadores están en memoria: con 2 instancias, cada una cuenta por separado | Solo importa si escalas; al lanzar habrá 1 instancia |
| IP real detrás del proxy de Railway | Configurado (`forward-headers-strategy: native`) | Verificar en el despliegue (fase 10) |
| Spring Boot 3.5 sin soporte gratuito | Ver §3 | Cuando decidas |
| Recargas por transferencia | Modelo de datos listo; falta la lógica y las pantallas | Fase 5 |

## 13. Siguiente fase

**Fase 3 — Perfil de anuncio y página principal:**
- Backend: subida y saneado de imágenes, CRUD del perfil de anuncio y endpoint público del anuncio vigente.
- Frontend: cliente de API con renovación automática del token, login y registro, panel con perfil de anuncio y portada con el anuncio a pantalla completa (sin iframe) y el desplegable de tiempo y ranking.
