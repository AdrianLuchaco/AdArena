# Fase 1 — Arquitectura, modelo de datos y estructura

> **Estado:** ✅ terminada (2026-09-26)
> **Resultado verificable:** `cd backend && ./mvnw test` → las migraciones se aplican en un PostgreSQL real y todas las protecciones de la base de datos funcionan.
> **Documento de referencia:** [`docs/ARCHITECTURE.md`](../ARCHITECTURE.md), el "plano" del proyecto. Este documento explica **cómo** se llegó a él y **qué hace cada archivo**.

---

## Índice

1. [Objetivo de la fase](#1-objetivo-de-la-fase)
2. [Preguntas que te hice y tus respuestas](#2-preguntas-que-te-hice-y-tus-respuestas)
3. [Decisiones que tomé yo (y por qué)](#3-decisiones-que-tomé-yo-y-por-qué)
4. [Conceptos que necesitas entender](#4-conceptos-que-necesitas-entender)
5. [Comandos que ejecuté](#5-comandos-que-ejecuté)
6. [Archivo por archivo](#6-archivo-por-archivo)
7. [Las tablas de la base de datos, una a una](#7-las-tablas-de-la-base-de-datos-una-a-una)
8. [Tests y cómo se verificó](#8-tests-y-cómo-se-verificó)
9. [Cambios posteriores a esta fase](#9-cambios-posteriores-a-esta-fase)
10. [Qué NO se hizo en esta fase (a propósito)](#10-qué-no-se-hizo-en-esta-fase-a-propósito)

---

## 1. Objetivo de la fase

Antes de escribir la lógica de negocio hay que decidir tres cosas que luego cuesta mucho cambiar:

1. **La arquitectura:** qué piezas hay (frontend, backend, base de datos, servicios externos) y cómo se hablan.
2. **El modelo de datos:** qué tablas existen, qué guardan y qué reglas impone la propia base de datos.
3. **La estructura de carpetas** de los dos proyectos, para que cada cosa tenga un sitio claro.

## 2. Preguntas que te hice y tus respuestas

| Pregunta | Tu respuesta | Consecuencia en el diseño |
|---|---|---|
| ¿Cómo encaja la moderación con la ventana de 24 h? | Solo se modera al ganador; la ventana es fija | Mientras no apruebes, la portada muestra el contenido base; las horas perdidas no se recuperan |
| ¿Qué pasa con el dinero de un ganador rechazado? | Devolución íntegra al saldo | Se devuelve el 100 % y el siguiente clasificado pasa a ser candidato |
| ¿Se puede retirar el saldo? | No, solo sirve para pujar | No hay flujo de retirada. Es un riesgo legal con consumidores (ver ARCHITECTURE §11) |
| ¿Quién puede pujar? | Cualquier persona (B2C) | Sin NIF obligatorio; aplican las normas de consumidores |

## 3. Decisiones que tomé yo (y por qué)

| Decisión | Por qué |
|---|---|
| **Empate:** gana quien llegó antes a ese total | Es lo estándar y se puede demostrar con la secuencia de pujas |
| **El arrastre se acumula:** si pierdes de nuevo, conservas el 50 % del nuevo total | Es la aplicación literal de la regla 7 |
| **Redondeo del arrastre hacia abajo** al céntimo | Así nunca aparece dinero de la nada: 1,01 € → arrastra 0,50 €, pierde 0,51 € |
| **Incremento mínimo** = mínimo de cada aportación extra | Las pujas se suman (regla 2), así que no tiene sentido exigir superar al líder de golpe |
| **Cambios de configuración** solo desde la siguiente subasta | Nadie cambia las reglas a mitad de partido; es más justo y más defendible legalmente |
| **Dinero en céntimos** (`long` en Java, `bigint` en SQL) | `double` tiene errores de redondeo (0,1 + 0,2 ≠ 0,3). Los enteros son exactos |
| **IDs UUID** generados por la aplicación | No se pueden adivinar en las URLs y se conocen antes de guardar |
| **Imágenes en PostgreSQL** | Un servicio externo menos al empezar; se puede mover a R2/S3 después |
| **URL del anunciante solo `https://`** | Protege a tus visitantes |
| **WebSocket + STOMP** para el tiempo real | Canales públicos (ranking) y privados ("te han superado") con autenticación de serie |
| **Railway** para backend y base de datos | Un solo panel, despliegue desde Git, precio bajo |
| **Spring Boot 3.5.16** | Pediste Spring Boot 3; es la última versión de esa rama |

## 4. Conceptos que necesitas entender

### 4.1 Flyway (migraciones)
Una **migración** es un archivo SQL numerado (`V1__...sql`, `V2__...sql`) que modifica la base de datos. Al arrancar, Flyway mira en la tabla `flyway_schema_history` cuáles se han aplicado ya y ejecuta solo las nuevas, en orden. Ventajas:
- La estructura de la base de datos está versionada en Git junto al código.
- Tu ordenador, los tests y producción tienen **exactamente** el mismo esquema.

**Regla de oro:** una migración que ya se aplicó en una base de datos real **nunca se edita**; se crea otra nueva (`V9__...`). Si editas una ya aplicada, Flyway detecta que su "huella" (checksum) cambió y se niega a arrancar.

### 4.2 JPA / Hibernate (entidades)
Una **entidad** es una clase Java que representa una fila de una tabla (`User` ↔ `users`). Hibernate traduce entre objetos y SQL. Configuré `ddl-auto: validate`: **Hibernate no crea ni cambia tablas**, solo comprueba al arrancar que las entidades encajan con lo que creó Flyway. Si no encajan, la aplicación no arranca, y así el error sale en tu ordenador y no en producción.

### 4.3 Entidades con comportamiento ("modelo rico")
Las entidades no son simples bolsas de datos: contienen las reglas que les afectan. Ejemplos:
- `auction.applyAntiSniping(horaPuja)` decide si se extiende el contador.
- `participation.addBid(...)` suma la puja y rechaza pujas sobre participaciones cerradas.
- `ledgerTransaction.post(cuenta, importe)` mueve dinero y rechaza dejar a un usuario en negativo.

Así la regla vive en un solo sitio y se puede probar sin base de datos (`AuctionDomainTest`).

### 4.4 El libro de movimientos (ledger) de partida doble
En lugar de un campo `saldo` que se sube y se baja, cada movimiento de dinero es una **transacción** con dos o más **apuntes** que suman **cero**. Ejemplo de una puja de 10 €:

| Cuenta | Importe |
|---|---:|
| Saldo libre de Ana | −10,00 |
| Saldo reservado de Ana | +10,00 |
| **Suma** | **0,00** |

El dinero nunca aparece ni desaparece, solo se mueve, y cualquier saldo se puede reconstruir sumando apuntes. Es lo que hacen bancos y contables.

### 4.5 Redes de seguridad en la base de datos
Aunque el código Java tuviera un bug, PostgreSQL impide estados imposibles:

| Protección | Cómo | Dónde |
|---|---|---|
| Toda transacción contable cuadra | Trigger **diferido** (se comprueba al hacer COMMIT) | V5 |
| El ledger no se edita ni se borra | Trigger que lanza un error ante UPDATE/DELETE | V5 |
| Un usuario nunca queda en negativo | `CHECK (balance_cents >= 0)` | V5 |
| La misma operación no se contabiliza dos veces | `UNIQUE (idempotency_key)` | V5 |
| Solo una subasta abierta a la vez | Índice único **parcial** (`WHERE status = 'OPEN'`) | V3 |
| Un solo candidato "vivo" por subasta | Índice único parcial | V4 |
| Una subasta cerrada tiene resultado y hora de cierre | `CHECK` | V3 |

### 4.6 Concurrencia (dos pujas a la vez)
Cada puja hará `SELECT ... FOR UPDATE` sobre la fila de la subasta: la segunda puja **espera** a que termine la primera. Siempre se bloquea en el mismo orden (subasta → participación → cuentas), lo que evita interbloqueos (*deadlocks*). Se implementará en la fase 4; en esta fase se preparó el modelo para ello.

### 4.7 Testcontainers
Una librería que, durante los tests, arranca un **PostgreSQL real dentro de Docker**, lo usa y lo destruye al terminar. Así probamos contra la misma base de datos que producción, con triggers y CHECK incluidos, cosa imposible con una base de datos en memoria tipo H2.

## 5. Comandos que ejecuté

```bash
# Comprobar herramientas instaladas
java -version; mvn -v; node -v; docker --version

# Consultar en Maven Central las últimas versiones disponibles
curl -s https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-parent/maven-metadata.xml

# Ejecutar los tests del backend (arranca PostgreSQL en Docker)
cd backend && mvn test

# Generar el proyecto del frontend
npx create-next-app@latest frontend --ts --tailwind --eslint --app --src-dir \
    --import-alias "@/*" --use-npm --disable-git --yes
```

## 6. Archivo por archivo

### 6.1 Raíz

| Archivo | Qué es |
|---|---|
| `README.md` | Portada del repositorio: qué es cada carpeta y cómo ejecutar lo básico |
| `.gitignore` | Lo que Git NO debe guardar: carpetas compiladas (`target/`, `.next/`), dependencias (`node_modules/`), secretos (`.env`) |
| `docs/ARCHITECTURE.md` | Arquitectura, reglas de negocio formalizadas, flujo del dinero, modelo de datos, concurrencia, tiempo real, autenticación, estructura y riesgos |

### 6.2 Backend: configuración

| Archivo | Qué es |
|---|---|
| `backend/pom.xml` | "Receta" de Maven: versión de Java (21), de Spring Boot (3.5.16) y librerías. En la fase 1 tenía web, JPA, Flyway, PostgreSQL, validación y tests |
| `backend/src/main/resources/application.yml` | Configuración: conexión a la base de datos (leída de variables de entorno con valores por defecto para local), `open-in-view: false` (no mantener la conexión abierta durante la respuesta), `ddl-auto: validate`, zona horaria UTC |
| `backend/src/main/java/com/publifi/PublifiApplication.java` | Punto de entrada: el `main` que arranca Spring Boot |

### 6.3 Backend: migraciones (`src/main/resources/db/migration/`)

| Archivo | Crea |
|---|---|
| `V1__users_and_auth.sql` | `users` y `refresh_tokens` |
| `V2__images_and_ad_profiles.sql` | `images` y `ad_profiles` |
| `V3__settings_and_auctions.sql` | `app_settings` (con valores por defecto), `auctions`, `auction_participations`, la secuencia `bid_seq` y `bids` |
| `V4__ad_slots.sql` | `ad_slots` (candidatos a emisión y moderación) |
| `V5__wallet_ledger.sql` | `ledger_accounts` (y las 2 cuentas del sistema), `ledger_transactions`, `ledger_entries`, los triggers de cuadre e inmutabilidad y la vista `ledger_account_mismatches` |
| `V6__payments.sql` | Recargas (ver §9: se rediseñó en la fase 2) |
| `V7__notifications_and_email.sql` | `notifications` y `email_outbox` |
| `V8__admin_audit_and_shedlock.sql` | `admin_audit_log` y `shedlock` |

Cada archivo lleva comentarios en español que explican cada tabla y cada restricción.

### 6.4 Backend: entidades (`src/main/java/com/publifi/`)

| Paquete | Clases | Qué representan |
|---|---|---|
| `common/domain` | `BaseEntity` | Base de todas las entidades: ID UUID generado en Java, `equals`/`hashCode` por ID, e `isNew()` para que Spring Data no haga un SELECT antes de cada INSERT |
| `user/domain` | `User`, `Role`, `RefreshToken` | Cuenta (email normalizado en minúsculas, rol, versión de términos aceptada) y sesiones |
| `image/domain` | `StoredImage` | Imagen saneada e inmutable |
| `adprofile/domain` | `AdProfile` | Perfil de anuncio: empresa, web, descripción, logo |
| `settings/domain` | `AppSettings` | Configuración global; `toAuctionRules()` hace la copia que se congela en cada subasta |
| `auction/domain` | `Auction`, `AuctionStatus`, `AuctionResult`, `AuctionRules` | Subasta diaria con su fin móvil (`endsAt`), las extensiones y las reglas congeladas. `applyAntiSniping()` implementa la regla 5 |
| `auction/domain` | `AuctionParticipation`, `ParticipationOutcome`, `AdSnapshot` | Total acumulado de un usuario en una subasta, resultado (`ACTIVE`/`WON`/`LOST`/`REFUNDED`) y foto del anuncio al cierre |
| `auction/domain` | `Bid`, `BidType` | Cada aportación (`BID`, `CARRY_OVER`, `CARRY_REVERSAL`), inmutable |
| `adslot/domain` | `AdSlot`, `AdSlotStatus` | Candidato a la ventana de emisión: `approve()`, `reject()`, `expire()`, `isLiveAt()` |
| `wallet/domain` | `LedgerAccount`, `LedgerAccountType`, `LedgerTransaction`, `LedgerTransactionType`, `LedgerEntry`, `InsufficientFundsException` | Contabilidad de partida doble. Solo `LedgerTransaction.post()` puede mover saldos |
| `payment/domain` | Recargas (ver §9) | |
| `notification/domain` | `Notification`, `NotificationType`, `OutboxEmail`, `OutboxEmailStatus` | Avisos en la web y emails pendientes con reintentos exponenciales (1, 2, 4, 8 min) |
| `admin/domain` | `AdminAuditLog` | Rastro inmutable de acciones de administración |

**Relaciones:** dentro de un mismo módulo se usan relaciones JPA (`Bid → AuctionParticipation`, `LedgerEntry → LedgerTransaction`). Entre módulos distintos se guarda solo el ID (`UUID userId`), lo que evita cargas perezosas inesperadas y el acoplamiento entre módulos.

### 6.5 Backend: tests (`src/test/java/com/publifi/`)

| Archivo | Qué comprueba |
|---|---|
| `TestcontainersConfiguration.java` | Arranca `postgres:17-alpine` en Docker y conecta Spring a él automáticamente (`@ServiceConnection`) |
| `schema/SchemaMigrationTest.java` | Las 8 migraciones se aplican; hay valores por defecto y cuentas del sistema; el trigger rechaza una transacción descuadrada al hacer COMMIT; el ledger no se puede editar; no puede haber dos subastas abiertas; un usuario no puede quedar en negativo |
| `schema/EntityMappingTest.java` | Guarda un ejemplo de **cada entidad** y lo vuelve a leer: usuario, sesión, imagen, perfil, subasta, puja, cuentas, recarga, ganador, notificación, email y auditoría |
| `auction/domain/AuctionDomainTest.java` | Sin base de datos: las pujas se suman (10 € + 6 € = 16 €); el anti-sniping extiende en los últimos 2 minutos, no antes, y se detiene en el máximo; el redondeo del arrastre; una participación cerrada no admite pujas |

### 6.6 Frontend

Generado con `create-next-app`: **Next.js 16.3**, React 19, TypeScript, Tailwind CSS 4 y ESLint. De momento es la plantilla inicial; las páginas se escriben a partir de la fase 3.

| Archivo | Qué es |
|---|---|
| `frontend/package.json` | Dependencias y scripts (`npm run dev`, `build`, `lint`). Renombrado a `publifi-frontend` |
| `frontend/src/app/layout.tsx`, `page.tsx`, `globals.css` | Plantilla inicial |
| `frontend/AGENTS.md`, `CLAUDE.md` | Notas que genera Next.js 16: esta versión cambia APIs respecto a versiones anteriores (por ejemplo, "middleware" ahora se llama "proxy"), así que antes de programar hay que consultar la documentación incluida en `node_modules/next/dist/docs/` |

## 7. Las tablas de la base de datos, una a una

| Tabla | Columnas clave | Restricciones importantes |
|---|---|---|
| `users` | `email`, `password_hash`, `role`, `enabled`, `accepted_terms_version/at` | Email único y en minúsculas; rol `USER`/`ADMIN` |
| `refresh_tokens` | `token_hash`, `family_id`, `expires_at`, `revoked_at` | Solo el hash; se borran en cascada con el usuario |
| `images` | `content_type`, `data`, `size_bytes`, `sha256` | Solo PNG/JPEG; máximo 2 MB |
| `ad_profiles` | `company_name`, `website_url`, `description`, `image_id` | Uno por usuario; URL `https://`; textos no vacíos |
| `app_settings` | mínimos, % de arrastre, hora de cierre, zona horaria, anti-sniping | Una sola fila (`id = 1`); valores por defecto: 1 €, 1 €, 50 %, 00:00 Europe/Madrid, 120 s / 120 s / 10 |
| `auctions` | `auction_date`, `opens_at`, `scheduled_end_at`, `ends_at`, `status`, `result`, copia de las reglas | Una por fecha; solo una abierta; coherencia entre estado, cierre y fechas |
| `auction_participations` | `total_cents`, `carried_in_cents`, `last_bid_seq`, `outcome`, `final_rank`, `forfeited/carried_out_cents`, `ad_*` | Una por usuario y subasta; importes ≥ 0; índice que sirve directamente el ranking |
| `bids` | `seq`, `type`, `amount_cents`, `total_after_cents`, `idempotency_key` | Signo del importe según el tipo; `(user, idempotency_key)` único |
| `ad_slots` | `candidate_rank`, `status`, `amount_cents`, `starts_at`, `ends_at`, datos de moderación | Un candidato vivo por subasta; un rechazo exige motivo |
| `ledger_accounts` | `user_id`, `type`, `balance_cents` | Las de usuario, nunca en negativo; una de cada tipo por usuario |
| `ledger_transactions` | `type`, `idempotency_key`, `reference_*` | Clave de idempotencia única; inmutables |
| `ledger_entries` | `amount_cents`, `balance_after_cents` | Distinto de 0; suman 0 por transacción; inmutables |
| `notifications` | `type`, `title`, `body`, `link`, `read_at` | Tipos cerrados |
| `email_outbox` | `to_email`, `subject`, `html_body`, `text_body`, `status`, `attempts`, `dedup_key` | `dedup_key` único (antispam) |
| `admin_audit_log` | `admin_id`, `action`, `target_*`, `details` (JSON) | — |
| `shedlock` | `name`, `lock_until` | Formato estándar de ShedLock |

## 8. Tests y cómo se verificó

Resultado al cerrar la fase 1: **15 tests, 0 fallos**:
- `AuctionDomainTest`: 7
- `SchemaMigrationTest`: 7
- `EntityMappingTest`: 1

Para comprobarlo tú (con Docker Desktop abierto):

```bash
cd backend
./mvnw test
```

## 9. Cambios posteriores a esta fase

Durante la fase 2 decidiste **no usar Stripe** y cobrar por **transferencia bancaria**. Como la base de datos todavía no existía en ningún entorno real, se modificaron las migraciones V5 y V6 directamente (esto solo es seguro **antes** del primer despliegue):
- La cuenta `STRIPE_CLEARING` pasó a llamarse `PAYMENTS_CLEARING`.
- `top_ups` ahora es independiente del proveedor: método, código de referencia, importe pedido y recibido, y admin que confirma.
- `stripe_events` se sustituyó por `payment_events`.
- Las entidades `TopUp`, `TopUpMethod`, `TopUpStatus` y `PaymentEvent` sustituyen a las de Stripe.

Los detalles están en [FASE-02.md](FASE-02.md#2-cambio-de-pagos-adiós-stripe-hola-transferencia).

## 10. Qué NO se hizo en esta fase (a propósito)

- Ningún endpoint, servicio ni repositorio: son de las fases 2 a 8.
- Nada de Docker Compose ni seguridad: fase 2.
- Ninguna página del frontend: fase 3 en adelante.
