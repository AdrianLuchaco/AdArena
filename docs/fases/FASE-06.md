# Fase 6 — El cierre diario: ganador, arrastre del 50 % y ronda siguiente

> **Estado:** ✅ terminada (2026-09-27)
> **Resultado verificable:**
> - A medianoche (hora de Madrid) la Arena se cierra sola, el 1.º pasa a moderación, los demás conservan el 50 % y se abre la ronda siguiente.
> - **Probado con tus datos reales:** tu ronda del 27 llevaba 11 horas "abierta" con el contador a cero. Al arrancar el backend nuevo se cerró sola en 4 segundos: ganó **Estudio Lumen con 72 €**, SacS, Huerta Viva y Bicis Norte conservaron el 50 % y se abrió la ronda del 28.
> - `ArenaCloseIntegrationTest`: 9 tests con el ejemplo de la documentación céntimo a céntimo.

---

## Índice

1. [El fallo que había](#1-el-fallo-que-había)
2. [Qué pasa a medianoche, paso a paso](#2-qué-pasa-a-medianoche-paso-a-paso)
3. [El ejemplo con euros](#3-el-ejemplo-con-euros)
4. [La ventana del ganador en portada](#4-la-ventana-del-ganador-en-portada)
5. [Por qué nunca se duplica ni se pierde nada](#5-por-qué-nunca-se-duplica-ni-se-pierde-nada)
6. [Las tareas automáticas](#6-las-tareas-automáticas)
7. [Probarlo sin esperar a medianoche](#7-probarlo-sin-esperar-a-medianoche)
8. [Archivo por archivo](#8-archivo-por-archivo)
9. [Tests](#9-tests)

---

## 1. El fallo que había

En la fase 4 la ronda se **abría** sola, pero no existía el código que la **cerraba**. A medianoche:
- nadie ganaba y no se cobraba ni devolvía nada;
- el dinero de todos se quedaba reservado para siempre;
- no se abría la ronda siguiente: la web se quedaba con el contador a 00:00:00 y "La Arena de hoy ya ha cerrado".

Es decir, **la web solo funcionaba el primer día**. Era lo más urgente de "que todo sea funcional".

## 2. Qué pasa a medianoche, paso a paso

Cada 5 segundos una tarea comprueba si la ronda abierta ya ha terminado (su `ends_at`, que puede haberse alargado por pujas de última hora). Cuando sí, `ArenaCloseService.closeDueRound()` hace **todo esto en una sola transacción**:

1. **Bloquea la ronda.** Desde ese instante ninguna puja puede colarse. Si ya estaba cerrada, no hace nada.
2. **Clasificación final:** mayor total primero; a igualdad, quien llegó antes.
3. **Nadie pujó** → la ronda queda `NO_BIDS` y al día siguiente la portada dice "Ayer nadie pujó" (regla 8).
4. **El 1.º gana:**
   - se guarda una **copia de su anuncio** tal como estaba (si luego edita su perfil, lo que se publica es la copia);
   - se crea su hueco en portada **pendiente de moderación**;
   - su dinero **sigue reservado**: se cobra cuando lo apruebes o se le devuelve entero si lo rechazas o no te da tiempo a moderarlo.
5. **Los demás** (regla 7):
   - conservan `50 %` de su total, **redondeado hacia abajo al céntimo**;
   - el resto pasa a tus ingresos (movimiento `BID_FORFEIT`);
   - también se guarda la copia de su anuncio para la página de Ganadores.
6. **Se abre la ronda siguiente** con las reglas vigentes en ese momento (si cambiaste la configuración, se aplica desde aquí).
7. **Arrastres:** cada perdedor empieza la ronda nueva con su 50 % como puja inicial, sin hacer nada. El dinero ya estaba reservado, así que no se mueve: solo se apunta. Se crean en orden de clasificación, así que a igualdad de arrastre va delante quien quedó mejor.
8. **Avisos:** al ganador, "¡Has ganado la Arena!" (web + email); a los demás, "La Arena ha terminado: conservas X €" (web).
9. **Todos los navegadores** reciben al instante la ronda nueva por WebSocket.

## 3. El ejemplo con euros

El mismo del §3.6 de `ARCHITECTURE.md`, comprobado por el test `theWinnerKeepsItsMoneyOnHoldAndTheOthersKeepHalfForTomorrow`:

Ana recarga 50 € y puja 10 + 6 = **16 €**. Luis recarga 20 € y puja **12 €**.

| Momento | Ana libre | Ana reservado | Luis libre | Luis reservado | Tus ingresos |
|---|---:|---:|---:|---:|---:|
| Tras las pujas | 34 | 16 | 8 | 12 | 0 |
| **Tras el cierre** | 34 | 16 *(retenido hasta moderar)* | 8 | 6 *(su puja inicial de mañana)* | **6** |

Redondeo: si Luis hubiera pujado 1,01 €, arrastraría 0,50 € y perdería 0,51 €. Nunca aparece dinero de la nada.

Si Luis pierde otra vez al día siguiente, conserva el 50 % de su **nuevo** total (arrastre + lo que añada). Y si al día siguiente nadie más puja, Luis puede ganar solo con su arrastre (test `theRunnerUpCanWinTomorrowWithItsCarryAlone`).

## 4. La ventana del ganador en portada

La ventana es **fija**: desde el fin programado de su ronda hasta el fin programado de la siguiente, normalmente de 00:00 a 00:00 (23 o 25 horas los días de cambio de hora). Si apruebas a las 9:00, se ve de 9:00 a 00:00: no se alarga. Así lo decidiste en la fase 1.

## 5. Por qué nunca se duplica ni se pierde nada

| Riesgo | Protección |
|---|---|
| La tarea se ejecuta dos veces a la vez (p. ej. dos copias del backend durante un despliegue) | Bloqueo de la fila de la ronda + comprobación de su estado: la segunda no encuentra nada que cerrar |
| Se cae el servidor a mitad del cierre | Una sola transacción: o se aplica entero o nada. Al volver, se cierra normalmente |
| Dos rondas abiertas | Índice único en la base de datos ("solo una abierta") |
| Dos rondas con la misma fecha (por ejemplo, si cambias la hora de cierre) | Se salta al siguiente cierre libre |
| Una puja justo a medianoche | La puja bloquea la ronda antes; si ya terminó, recibe `ROUND_CLOSED` |
| Interbloqueos entre el cierre y otras operaciones | Orden de bloqueo único en toda la aplicación: ronda → participaciones → cuentas de todos los usuarios implicados (a la vez y por id) → cuentas del sistema |
| El dinero no cuadra | Cada movimiento suma cero (lo comprueba PostgreSQL al guardar) y los tests verifican la vista `ledger_account_mismatches` tras cada escenario |

## 6. Las tareas automáticas

`common/jobs/ScheduledJobs.java` (sustituye al antiguo `ArenaScheduler`):

| Cada | Tarea |
|---|---|
| 5 s | Cerrar la ronda si ya terminó; abrir una si no hay ninguna |
| 1 min | Caducar anuncios ganadores sin moderar cuya ventana terminó (devolución del 100 %) y recargas sin pagar |
| 10 s | Enviar los emails pendientes |

Se ejecutan en sus propios hilos (`adarena-jobs-N`, `SchedulingConfig`), separados de los del WebSocket. Un fallo en una tarea se registra en el log y se reintenta en la vuelta siguiente. Se pueden desactivar con `JOBS_ENABLED=false` (los tests lo hacen para decidir ellos cuándo pasa cada cosa).

## 7. Probarlo sin esperar a medianoche

Solo en tu ordenador: **Administración → Resumen → "Terminar la Arena ahora"** (recuadro amarillo). Adelanta el fin de la ronda a este instante y ejecuta el cierre normal. Así puedes probar todo el ciclo: ganador, arrastre, moderación y anuncio en portada.

Por dentro es `POST /api/dev/arena/close-now`, que **solo existe con el perfil `dev`** y además exige rol de administrador. En producción responde 404 (lo comprueba `SecurityHardeningTest`).

## 8. Archivo por archivo

| Archivo | Nuevo/Mod. | Qué hace |
|---|---|---|
| `auction/service/ArenaCloseService.java` | Nuevo | El cierre (§2) |
| `auction/service/ArenaLifecycleService.java` | Mod. | `planRound()`: ronda nueva sin repetir fecha; lo usan el cierre y la apertura |
| `auction/domain/AuctionParticipation.java` | Mod. | `markWithdrawn()` para participaciones que se quedaron en 0 € (ver fase 8) |
| `auction/repository/AuctionParticipationRepository.java` | Mod. | Clasificación completa, "a quién pasó su arrastre", siguientes clasificados, suma de lo que hay en juego |
| `adprofile/repository/AdProfileRepository.java` | Mod. | Perfiles de varios usuarios de una vez (copias de los anuncios) |
| `common/jobs/ScheduledJobs.java` | Nuevo | Todas las tareas automáticas |
| `common/config/SchedulingConfig.java` | Mod. | Hilos propios para las tareas |
| `admin/controller/DevToolsController.java` | Nuevo | "Terminar la Arena ahora" (solo local) |
| `auction/service/ArenaScheduler.java` | Borrado | Sustituido por `ScheduledJobs` |
| `application.yml` | Mod. | `app.jobs.enabled` (`JOBS_ENABLED`) sustituye a `ARENA_AUTO_OPEN` |

## 9. Tests

`ArenaCloseIntegrationTest` (9 tests contra PostgreSQL real):

| Test | Qué comprueba |
|---|---|
| `nothingHappensBeforeTheCountdownEnds` | No se cierra antes de tiempo |
| `anEmptyDayClosesWithoutWinnerAndTheNextRoundOpens` | Día vacío: `NO_BIDS`, sin hueco en portada, ronda nueva abierta que acepta pujas |
| `closingTwiceNeverDuplicatesAnything` | Idempotente: un solo hueco y una sola ronda abierta |
| `theWinnerKeepsItsMoneyOnHoldAndTheOthersKeepHalfForTomorrow` | El ejemplo del §3 céntimo a céntimo: saldos, ingresos, copias de los anuncios, hueco y su ventana, arrastre con su puja `CARRY_OVER`, avisos y email al ganador |
| `theCarryIsRoundedDownToTheCent` | 1,01 € → 0,50 € arrastrados y 0,51 € perdidos |
| `onATieWhoeverGotThereFirstWins` | Desempate |
| `theRunnerUpCanWinTomorrowWithItsCarryAlone` | Regla 7: el 2.º gana al día siguiente solo con su arrastre |
| `losingAgainKeepsHalfOfTheNewTotal` | El arrastre se acumula: 50 % del nuevo total |
| `bidsAfterTheCloseGoToTheNewRound` | Tras el cierre, las pujas van a la ronda nueva |
