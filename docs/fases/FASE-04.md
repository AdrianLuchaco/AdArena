# Fase 4 — AdArena: pujas, clasificación en directo, contador y anti-sniping

> **Estado:** ✅ terminada (programada el 2026-09-26, documentada y revisada el 2026-09-27)
> **Resultado verificable:**
> - `./dev.sh` → **http://localhost:3000**: portada con la clasificación de hoy, la Arena con la tarjeta para pujar y la página de Ganadores.
> - Una puja cambia la clasificación **al instante** en todas las pantallas abiertas (WebSocket).
> - `cd backend && ./mvnw test` → todos los tests de pujas, incluidas 20 y 40 pujas **simultáneas**, pasan.

Este documento cubre el trabajo de la sesión del 26 de septiembre, que terminó sin documentar porque se agotó el límite de uso. Lo he revisado entero el día 27 antes de seguir.

---

## Índice

1. [Qué pediste y qué se hizo](#1-qué-pediste-y-qué-se-hizo)
2. [El cambio de nombre: Publifi → AdArena](#2-el-cambio-de-nombre-publifi--adarena)
3. [Sin la palabra "subasta": la Arena](#3-sin-la-palabra-subasta-la-arena)
4. [Cómo funciona una puja por dentro](#4-cómo-funciona-una-puja-por-dentro)
5. [Tiempo real: WebSocket + STOMP](#5-tiempo-real-websocket--stomp)
6. [La ronda del día se abre sola](#6-la-ronda-del-día-se-abre-sola)
7. [Saldo de prueba (solo en tu ordenador)](#7-saldo-de-prueba-solo-en-tu-ordenador)
8. [La API nueva](#8-la-api-nueva)
9. [La web](#9-la-web)
10. [Un bloqueo real que encontraron los tests](#10-un-bloqueo-real-que-encontraron-los-tests)
11. [Archivo por archivo](#11-archivo-por-archivo)
12. [Tests](#12-tests)
13. [Lo que quedó pendiente (y se resolvió en las fases 5–8)](#13-lo-que-quedó-pendiente-y-se-resolvió-en-las-fases-58)

---

## 1. Qué pediste y qué se hizo

| Lo que pediste | Qué se hizo |
|---|---|
| Que la gente pueda ver los proyectos que compiten | La portada muestra la clasificación de hoy con cada proyecto: imagen, nombre, descripción, web e importe |
| Algo más de efectos, pero simple | Apariciones suaves al hacer scroll, importes que "suben" animados, destello cuando alguien puja, contador con dígitos que caen, luces que flotan en la cabecera y avisos emergentes. Todo se desactiva si el sistema pide menos movimiento |
| Que la primera página sea la lista hasta que haya ganador | Sin ganador aprobado, la portada es: cabecera con cuenta atrás + la clasificación. Con ganador, su anuncio arriba y la clasificación debajo |
| Que los días anteriores tengan la descripción de los proyectos | Página **Ganadores** (`/ganadores`): cada día, su ganador y todos los que compitieron, tal y como eran sus anuncios ese día |
| No decir "subasta" | En toda la web se habla de **la Arena** (ver §3) |
| Siguiente fase | Fase 4 completa: pujar, clasificación al instante, anti-sniping, avisos "te han superado" |
| Cambiar el nombre a AdArena | Nombre cambiado en la web **y por dentro** (ver §2) |

## 2. El cambio de nombre: Publifi → AdArena

No solo el texto visible: todo el proyecto, para que no queden restos que confundan en el futuro.

| Dónde | Antes | Ahora |
|---|---|---|
| Paquete Java | `com.publifi` | `com.adarena` |
| Clase principal | `PublifiApplication` | `AdArenaApplication` |
| Base de datos local | `publifi` | `adarena` |
| Cookie de sesión | `publifi_refresh` | `adarena_refresh` |
| Emisor de los JWT | `publifi` | `adarena` |
| Cuentas de prueba | `@publifi.local` | `@adarena.local` (contraseñas `AdminAdArena2026!` y `DemoAdArena2026!`) |
| Docker Compose | proyecto `publifi` | proyecto `adarena` |

`dev.sh` detecta si la base de datos antigua (`publifi-postgres-1`) está en marcha y la para **sin borrar sus datos**: el volumen `publifi_postgres-data` se conserva por si acaso. Si algún día quieres liberar ese espacio: `docker volume rm publifi_postgres-data`.

Los documentos de las fases 1–3 siguen diciendo "Publifi": son el registro de lo que se hizo entonces.

## 3. Sin la palabra "subasta": la Arena

| En la web se dice | Significa |
|---|---|
| **La Arena** / la Arena de hoy | La subasta del día |
| Pujar / tu puja | Igual (la palabra "puja" sí se mantiene: es clara y no suena a subasta de objetos) |
| Clasificación | El ranking |
| Ganadores | Historial de subastas |

**Por dentro** (código y base de datos) se siguen usando `auction`, `bid`, etc. Son términos técnicos que ningún usuario ve, y cambiarlos habría obligado a reescribir las migraciones de la base de datos.

## 4. Cómo funciona una puja por dentro

Todo ocurre en **una única transacción** (`BidService.placeBid`). Si cualquier paso falla, no se guarda nada.

1. **Comprobaciones rápidas:** clave de idempotencia válida, importe > 0, importe ≤ 10.000 € y que tengas anuncio.
2. **Bloqueo de la ronda** (`SELECT … FOR UPDATE`). Desde aquí, las pujas del día van de una en una. Con decenas o cientos de pujas diarias el coste es despreciable, y a cambio el ranking y los saldos siempre son exactos.
3. **Idempotencia:** si esa `Idempotency-Key` ya se usó, se devuelve tu situación sin cobrar otra vez (doble clic, reintento por mala conexión). Se comprueba **después** del bloqueo para que dos reintentos simultáneos no pasen los dos.
4. **¿Está abierta?** Si el contador ya llegó a cero → `ROUND_CLOSED`.
5. **Mínimos:** la primera aportación del día ≥ puja mínima (1 €); las siguientes ≥ incremento mínimo (1 €). No hace falta superar al primero: las pujas se suman.
6. **Saldo:** se bloquean tus cuentas y se comprueba el saldo libre.
7. **Se apunta:** tu total sube (`auction_participations`), se guarda la aportación con su número de orden global (`bids.seq`) y el dinero pasa de *libre* a *reservado* en el libro de movimientos (`BID_RESERVE`).
8. **Anti-sniping:** si quedaban 2 minutos o menos, el fin se retrasa 2 minutos (máximo 10 veces por ronda).
9. **¿A quién has superado?** A quien iba por delante de ti antes de pujar y ahora va por detrás. Esas personas reciben el aviso "te han superado".
10. **Se anuncia** el cambio por WebSocket, pero solo **después** de guardar (ver §5).

**Desempate:** a igualdad de total va delante quien llegó antes a ese total (menor `last_bid_seq`). Usar un número de secuencia en lugar de la hora evita empates de milisegundos.

## 5. Tiempo real: WebSocket + STOMP

| Canal | Quién lo escucha | Qué llega |
|---|---|---|
| `/topic/arena` | Cualquiera (también sin cuenta) | La portada entera: ranking, fin de la ronda, anuncio actual y hora del servidor |
| `/user/queue/notifications` | Solo tú (con sesión) | Tus avisos privados: "te han superado" y, desde la fase 7, todos los demás |

**Seguridad del WebSocket** (`StompAuthInterceptor`):
- La conexión solo se acepta desde los orígenes del frontend.
- El JWT va dentro del mensaje `CONNECT` de STOMP. Sin token = visitante anónimo (solo el canal público).
- Solo se puede suscribir a esos dos canales. Nadie puede escuchar los avisos de otro.
- Los clientes **no pueden enviar** mensajes: las pujas van siempre por la API REST.

**`ArenaBroadcaster`** difunde los cambios:
- **Después del commit** (`@TransactionalEventListener(AFTER_COMMIT)`): nunca se anuncia una puja que luego se deshace.
- **En su propio hilo**: la puja devuelve su conexión a la base de datos sin esperar al envío (ver §10).
- **Agrupando**: si llegan 40 pujas en un segundo, no se envían 40 rankings, sino el más reciente.

**El contador** no se envía cada segundo: el servidor manda `endsAt` y `serverTime`, y el navegador cuenta solo, corrigiendo el desfase de su reloj. Si el WebSocket se corta, se reconecta a los 3 segundos; y por si acaso, la web vuelve a pedir los datos cada minuto.

## 6. La ronda del día se abre sola

`ArenaLifecycleService.ensureOpenRound()`: si no hay ninguna ronda abierta, abre una que termina en el próximo cierre (00:00 de Madrid). Los días de cambio de hora la ronda dura 23 o 25 horas porque se calcula con fechas de calendario.

> ⚠️ **Lo que faltaba (corregido en la fase 6):** en esta fase la ronda se abría, pero **nunca se cerraba**. A medianoche nadie ganaba y la web se quedaba parada con el contador a cero. Lo encontré al revisar el código el día 27: tu ronda del 27 seguía "abierta" once horas después de medianoche. Ver [FASE-06.md](FASE-06.md).

## 7. Saldo de prueba (solo en tu ordenador)

Como las recargas reales llegaron en la fase 5, se añadió `POST /api/dev/wallet/test-funds`, que da hasta 1.000 € de saldo de prueba por petición. Existe **solo con el perfil `dev`** (`@Profile("dev")`): en producción esa ruta no existe y responde 404 (un test lo comprueba). Contablemente es una recarga normal: pasa por el libro de movimientos y las cuentas cuadran.

## 8. La API nueva

| Método | Ruta | Acceso | Qué hace |
|---|---|---|---|
| POST | `/api/arena/bids` | Sesión + `Idempotency-Key` | Pujar (añadir a tu total) |
| GET | `/api/arena/me` | Sesión | Tu situación hoy: total, posición, mínimo de tu próxima puja y saldo |
| GET | `/api/arena/me/bids?limit=50` | Sesión | Tu historial de pujas (máximo 100) |
| GET | `/api/me/wallet` | Sesión | Tu saldo libre y reservado |
| GET | `/api/public/history?page=0&size=10` | Público | Días anteriores con ganador y proyectos (máximo 20 por página) |
| POST | `/api/dev/wallet/test-funds` | Sesión, **solo en local** | Saldo de prueba |
| WS | `/ws` | Público (token opcional) | Tiempo real (§5) |

**Códigos de error nuevos:**

| HTTP | `code` | Cuándo |
|---|---|---|
| 400 | `IDEMPOTENCY_KEY_REQUIRED` | Falta la cabecera o no tiene el formato correcto |
| 409 | `NO_OPEN_ROUND` | No hay ronda abierta |
| 409 | `ROUND_CLOSED` | El contador ya llegó a cero |
| 422 | `BID_TOO_LOW` / `BID_TOO_HIGH` | Por debajo del mínimo o más de 10.000 € de golpe |
| 422 | `AD_PROFILE_REQUIRED` | Aún no has creado tu anuncio |
| 422 | `INSUFFICIENT_FUNDS` | No tienes saldo libre suficiente |
| 429 | `RATE_LIMITED` | Más de 60 pujas por minuto desde la misma IP |

## 9. La web

| Página | Qué hay |
|---|---|
| `/` | Sin ganador: cabecera oscura con "¿Quién ocupará esta portada mañana?" y cuenta atrás. Con ganador: su anuncio a pantalla completa y el desplegable "En directo". Debajo, siempre, la clasificación de hoy y "Cómo funciona" |
| `/arena` | Cuenta atrás grande, clasificación en directo y la tarjeta **Tu puja**: tu total, tu posición, tu saldo, importes rápidos, botón "Ponerme primero" (calcula cuánto te falta) y la casilla de la regla del 50 % (solo la primera vez) |
| `/ganadores` | Días anteriores con su ganador y todos los proyectos |
| `/panel/saldo` | Tu saldo (y, en local, botones de saldo de prueba) |
| `/panel/pujas` | Cada movimiento de tus pujas: puja, arrastre de ayer, arrastre retirado |

**Cómo se evita cobrar dos veces:** cada intento de puja genera una clave única (`crypto.randomUUID()`). Si la red falla, el reintento usa **la misma clave** y el servidor no vuelve a cobrar. Si el error es definitivo (saldo insuficiente…), la siguiente puja usa una clave nueva.

> El diseño de la clasificación, del ganador y de la ficha de cada proyecto se rehízo en la fase 8, a petición tuya. Ver [FASE-08.md](FASE-08.md).

## 10. Un bloqueo real que encontraron los tests

El test de **40 pujas simultáneas** se quedaba colgado. La causa:

1. Cada puja ocupa una conexión a la base de datos (el grupo tiene 10).
2. Al terminar, el aviso por WebSocket leía el ranking y pedía **otra** conexión… mientras la puja aún tenía la suya.
3. Con 10 pujas a la vez, las 10 conexiones estaban ocupadas y todas esperaban una undécima que nunca llegaba: un **bloqueo**.

**Solución:** el aviso sale en un hilo aparte, después de que la puja haya devuelto su conexión, y agrupando los cambios (§5). Este fallo solo habría aparecido con mucho tráfico real: justo el peor momento.

## 11. Archivo por archivo

### Backend
| Archivo | Qué hace |
|---|---|
| `auction/service/BidService.java` | Pujar (§4) |
| `auction/service/ArenaLifecycleService.java` | Abrir la ronda si no hay ninguna; `nextCloseAfter` calcula el próximo cierre |
| `auction/service/ArenaQueryService.java` | "Tu situación hoy" y tu historial de pujas |
| `auction/controller/ArenaController.java` | `/api/arena/**` |
| `auction/dto/BidRequest.java`, `BidResponse.java`, `MyArenaStatus.java` | Entrada y salida de la API de pujas |
| `auction/event/ArenaChangedEvent.java` | "Algo ha cambiado en la Arena" (se difunde tras el commit) |
| `auction/repository/*` | Ranking con desempate, bloqueos `FOR UPDATE`, "cuántos van por delante", historial |
| `realtime/WebSocketConfig.java` | Endpoint `/ws` y canales |
| `realtime/StompAuthInterceptor.java` | Seguridad del WebSocket (§5) |
| `realtime/ArenaBroadcaster.java` | Difusión del ranking y de los avisos privados |
| `realtime/ArenaNotification.java` | Formato de un aviso privado |
| `home/service/HistoryService.java`, `home/dto/HistoryPage.java` | Página de Ganadores |
| `wallet/controller/WalletController.java`, `DevWalletController.java` | Saldo y saldo de prueba |
| `wallet/service/WalletService.java` | Reserva de saldo al pujar |

### Frontend
| Archivo | Qué hace |
|---|---|
| `lib/realtime.ts` | Cliente STOMP con reconexión |
| `lib/arena-context.tsx` | Estado de la Arena compartido por toda la web |
| `components/arena/ArenaClient.tsx`, `BidPanel.tsx`, `Countdown.tsx` | Página de la Arena |
| `components/home/ArenaHero.tsx`, `HomeClient.tsx`, `LiveDropdown.tsx`, `HowItWorks.tsx` | Portada |
| `components/history/HistoryClient.tsx` | Ganadores |
| `components/panel/WalletView.tsx`, `MyBidsView.tsx` | Saldo y Mis pujas |
| `components/fx/*` | Efectos: `AnimatedNumber`, `FlashOnChange`, `Reveal` |
| `components/ui/Toaster.tsx` | Avisos emergentes |

## 12. Tests

| Clase | Tests | Qué comprueba |
|---|---:|---|
| `BidServiceIntegrationTest` | 13 | Reserva de saldo, pujas que se suman, mínimos, saldo insuficiente, anuncio obligatorio, ronda cerrada, idempotencia, anti-sniping, desempate, "te han superado", **20 pujas simultáneas del mismo usuario** (nunca gasta más de su saldo) y **40 pujas simultáneas de 8 usuarios** (totales exactos, 40 números de orden distintos, cuentas cuadradas) |
| `ArenaApiIntegrationTest` | 4 | La API por HTTP, códigos de error, sesión obligatoria, saldo de prueba inexistente fuera de local |
| `ArenaWebSocketIntegrationTest` | 2 | Con un WebSocket real: todos reciben el ranking nuevo; solo el superado recibe el aviso privado |
| `StompAuthInterceptorTest` | 5 | Anónimos, token válido, token inválido, canal privado exige sesión, canales prohibidos y envío prohibido |
| `HistoryIntegrationTest` | 1 | Días anteriores con ganador y la descripción de cada proyecto |
| `ArenaLifecycleTest` | 4 | Cierre a medianoche de Madrid, día del cambio de hora (25 h), una sola ronda abierta |

## 13. Lo que quedó pendiente (y se resolvió en las fases 5–8)

| Pendiente | Resuelto en |
|---|---|
| **La ronda no se cerraba nunca** | [Fase 6](FASE-06.md) |
| Recargar saldo de verdad | [Fase 5](FASE-05.md) |
| Emails ("te han superado", "has ganado") y la sección Avisos | [Fase 7](FASE-07.md) |
| Aprobar o rechazar al ganador | [Fase 8](FASE-08.md) |
| Documentación de esta fase | Este documento |
