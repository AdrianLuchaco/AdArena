# Fase 5 — Monedero y recargas por transferencia bancaria

> **Estado:** ✅ terminada (2026-09-27)
> **Resultado verificable:**
> - En **Mi panel → Saldo** eliges un importe y obtienes el IBAN y un código (`ADA-…`) para el concepto.
> - En **Administración → Recargas** buscas ese código y confirmas lo que ha llegado: el saldo aparece al momento y el usuario recibe un aviso.
> - Probado de punta a punta en el navegador: recarga de 37,50 € pedida por Bicis Norte, confirmada por el admin, aviso al instante y saldo actualizado.

---

## Índice

1. [Cómo funciona, paso a paso](#1-cómo-funciona-paso-a-paso)
2. [Por qué transferencia y no tarjeta](#2-por-qué-transferencia-y-no-tarjeta)
3. [El dinero en el libro de movimientos](#3-el-dinero-en-el-libro-de-movimientos)
4. [Reglas y límites](#4-reglas-y-límites)
5. [La API](#5-la-api)
6. [La web](#6-la-web)
7. [Configuración](#7-configuración)
8. [Archivo por archivo](#8-archivo-por-archivo)
9. [Tests](#9-tests)
10. [Pendiente y mejoras futuras](#10-pendiente-y-mejoras-futuras)

---

## 1. Cómo funciona, paso a paso

```
Usuario                          AdArena                               Tú (admin)
───────                          ───────                               ──────────
"Quiero recargar 37,50 €"  ──▶   Crea la recarga PENDIENTE
                                 con el código ADA-MN4MM2
                          ◀──    Muestra IBAN + beneficiario +
                                 importe + concepto (con botones de copiar)
Transferencia desde su banco
con "ADA-MN4MM2" en el concepto ─────────────────────────────────────▶ Te llega a tu banco
                                                                      Buscas ADA-MN4MM2 en
                                                                      Administración → Recargas
                                 ◀── "Ha llegado el dinero": 37,50 €
                                 Abona 37,50 € en su saldo libre
                          ◀──    Aviso "Recarga confirmada" (web + email)
```

- El código se genera con un generador aleatorio seguro y un alfabeto sin caracteres que se confunden (sin O/0 ni I/1): `ADA-` + 6 caracteres ≈ mil millones de combinaciones.
- **Se abona lo que llega de verdad**, no lo que el usuario dijo: si pidió 37,50 € y llegan 37,00 €, se abonan 37,00 €.
- El saldo **nunca** se abona por algo que diga el navegador. Solo tú (o, en el futuro, un proceso que lea el extracto del banco) puedes confirmarlo.
- Si en 7 días no llega nada, la recarga **caduca**. Si la transferencia llega tarde, aún puedes confirmarla.
- El usuario puede **anular** una recarga que todavía no ha pagado.

## 2. Por qué transferencia y no tarjeta

Lo decidiste en la fase 2: sin Stripe y sin comisiones. Recibir una transferencia no suele costar nada y el usuario elige el importe. El inconveniente es que no es instantáneo: hasta que tú confirmas, el saldo no aparece. Con transferencias **inmediatas** (Bizum empresa o SEPA Instant) el dinero llega en segundos y solo depende de lo rápido que lo confirmes.

El modelo está preparado para añadir más adelante una pasarela de tarjeta (`method = CARD`, `provider`, tabla `payment_events` para notificaciones idempotentes) sin tocar el resto.

## 3. El dinero en el libro de movimientos

Al confirmar se crea **un** movimiento `TOP_UP` con dos apuntes que suman cero:

| Cuenta | Importe |
|---|---:|
| `PAYMENTS_CLEARING` (dinero que ha entrado de fuera) | −37,50 € |
| Saldo libre del usuario | +37,50 € |

La clave de idempotencia es `topup:<id de la recarga>`: aunque se pulse "Confirmar" dos veces a la vez, la base de datos solo acepta un movimiento. Además, la recarga se **bloquea** (`SELECT … FOR UPDATE`) antes de confirmarla y se comprueba que siga pendiente.

**Referencia del banco (opcional):** si al confirmar escribes el identificador del movimiento en tu banco, la base de datos impide usarlo para confirmar otra recarga (restricción única `provider + provider_payment_id`). Así nunca abonas dos veces la misma transferencia por error.

**Comprobación permanente:** `Σ saldos de los usuarios + tus ingresos = total recargado`. El panel de administración lo muestra en verde ("Las cuentas cuadran al céntimo"). En tus datos locales, tras las pruebas: 223,50 + 205,00 + 71,50 = 500,00 €.

## 4. Reglas y límites

| Regla | Valor | Dónde se cambia |
|---|---|---|
| Importe mínimo por recarga | 5 € | `MIN_TOP_UP_CENTS` |
| Importe máximo por recarga | 5.000 € | `MAX_TOP_UP_CENTS` |
| Recargas sin pagar a la vez por usuario | 3 | `app.payments.max-pending-top-ups` |
| Plazo para hacer la transferencia | 7 días | `app.payments.top-up-validity` |
| Recargas pedidas por IP | 20 por hora | rate limit `top-ups` |
| Importe máximo que el admin puede confirmar | 100.000 € (protección contra errores al teclear) | `TopUpService.MAX_CONFIRM_CENTS` |
| ¿Se puede retirar el saldo? | **No** (decisión de la fase 1) | — |

## 5. La API

| Método | Ruta | Acceso | Qué hace |
|---|---|---|---|
| GET | `/api/me/wallet/overview` | Sesión | Saldo, si las recargas están activas, límites, datos bancarios y tus últimas 20 recargas |
| POST | `/api/me/top-ups` | Sesión | Pedir una recarga `{"amountCents": 3750}` → 201 con el código y el IBAN |
| POST | `/api/me/top-ups/{id}/cancel` | Sesión | Anular una recarga pendiente (solo las tuyas: la de otro "no existe", 404) |
| GET | `/api/admin/top-ups?status=PENDING` | Admin | Recargas por estado |
| GET | `/api/admin/top-ups?q=MN4MM2` | Admin | Buscar por código (vale un trozo) |
| POST | `/api/admin/top-ups/{id}/confirm` | Admin | `{"receivedCents": 3750, "bankReference": "…"}` → abona el saldo |
| POST | `/api/admin/top-ups/{id}/reject` | Admin | `{"reason": "…"}` → la descarta (no mueve dinero) y avisa al usuario |

**Códigos de error:**

| HTTP | `code` | Cuándo |
|---|---|---|
| 503 | `TOP_UPS_UNAVAILABLE` | No has configurado tu IBAN |
| 422 | `TOP_UP_AMOUNT_OUT_OF_RANGE` | Fuera de 5 € – 5.000 € |
| 409 | `TOO_MANY_PENDING_TOP_UPS` | Ya tiene 3 recargas sin pagar |
| 409 | `TOP_UP_NOT_PENDING` | Ya confirmada, descartada o anulada |
| 409 | `BANK_REFERENCE_ALREADY_USED` | Esa referencia del banco ya confirmó otra recarga |
| 404 | `TOP_UP_NOT_FOUND` | No existe (o es de otro usuario) |

## 6. La web

**Mi panel → Saldo** (`/panel/saldo`):
- Tu saldo disponible y lo que tienes en pujas o retenido.
- **Recargar saldo:** importe libre con botones rápidos (10, 25, 50, 100 €) → "Obtener los datos para la transferencia".
- **Tus recargas:** cada una con su estado. Las pendientes muestran los datos para transferir (importe, IBAN, beneficiario, banco y **concepto**), cada uno con su botón **Copiar**, y un aviso destacado: "Pon el código en el concepto".
- Cuando confirmas una recarga, al usuario le llega el aviso al instante y **su pantalla de saldo se actualiza sola**.
- En tu ordenador sigue estando el saldo de prueba (recuadro amarillo discontinuo). En producción no aparece.

**Administración → Recargas** (`/admin/recargas`): ver [FASE-08.md](FASE-08.md).

## 7. Configuración

| Variable | Obligatoria | Ejemplo | Para qué |
|---|---|---|---|
| `BANK_IBAN` | Sí, para activar las recargas | `ES12 3456 …` | Tu IBAN (cuenta de empresa o autónomo) |
| `BANK_BENEFICIARY` | Sí | `Tu Nombre SL` | Titular de la cuenta |
| `BANK_NAME` | No | `Tu banco` | Se muestra al usuario |
| `MIN_TOP_UP_CENTS` / `MAX_TOP_UP_CENTS` | No | `500` / `500000` | Límites |

En local, `application-dev.yml` usa un **IBAN de ejemplo** de la documentación bancaria (`ES91 2100 0418 4502 0005 1332`) y el beneficiario "AdArena (datos de ejemplo)". No es una cuenta real.

## 8. Archivo por archivo

| Archivo | Nuevo/Mod. | Qué hace |
|---|---|---|
| `payment/service/TopUpService.java` | Nuevo | Pedir, anular, listar, confirmar, descartar y caducar recargas |
| `payment/repository/TopUpRepository.java` | Nuevo | Consultas, bloqueo `FOR UPDATE`, búsqueda por código y caducidad en bloque |
| `payment/controller/TopUpController.java` | Nuevo | Rutas del usuario |
| `payment/dto/TopUpRequest.java`, `TopUpResponse.java`, `WalletOverview.java`, `AdminTopUpView.java` | Nuevos | Entrada y salida |
| `payment/domain/TopUp.java` | Mod. | Una recarga caducada también se puede confirmar |
| `wallet/service/WalletService.java` | Mod. | Bloqueo de cuentas de varios usuarios a la vez y en orden, `record()` y totales para el panel |
| `wallet/repository/LedgerAccountRepository.java` | Mod. | Bloqueo de varias cuentas y totales por tipo |
| `common/config/AppProperties.java` | Mod. | Bloque `payments` (IBAN, límites, plazo) |
| `application.yml`, `application-dev.yml`, `application-test.yml` | Mod. | Configuración de las recargas |
| `frontend/src/components/panel/WalletView.tsx` | Reescrito | Saldo, recargar, instrucciones con botones de copiar e historial |
| `frontend/src/lib/api.ts`, `types.ts` | Mod. | Llamadas y tipos nuevos |

## 9. Tests

`TopUpApiIntegrationTest` (8 tests, todos por HTTP como lo haría la web):

| Test | Qué comprueba |
|---|---|
| `theWholeFlowFromRequestToBalance` | Código `ADA-XXXXXX` válido, IBAN, confirmación con lo realmente recibido (37,00 de 37,50), saldo, aviso "recarga confirmada", cuentas cuadradas y que no se puede confirmar dos veces |
| `theSameBankMovementCannotConfirmTwoTopUps` | La misma referencia del banco no abona dos recargas |
| `amountsOutsideTheLimitsAreRejected` | 4,99 € y 5.000,01 € rechazados |
| `atMostThreeUnpaidTopUps` | La cuarta recarga sin pagar se rechaza |
| `usersCanCancelOnlyTheirOwnPendingTopUps` | Nadie puede anular la recarga de otro; anulada, ya no se puede confirmar |
| `aLateTransferCanStillBeConfirmedAfterExpiry` | Una caducada se puede confirmar si el dinero llega tarde |
| `theAdminCanDiscardATopUpAndTheUserIsTold` | Descartar no mueve dinero y avisa al usuario |
| `onlyAdminsCanConfirmOrListTopUps` | 403 para usuarios, 401 sin sesión; búsqueda por código |

## 10. Pendiente y mejoras futuras

- **Importar el extracto del banco** (Norma 43 o CSV) para casar los códigos automáticamente, en lugar de confirmar una a una. Con poco volumen, la confirmación manual es suficiente.
- **Pasarela de tarjeta** si en el futuro compensa pagar la comisión a cambio de recargas instantáneas.
- **Legal:** el saldo prepagado no retirable de consumidores tiene riesgo legal (ver `ARCHITECTURE.md` §11 y `AUDITORIA-SEGURIDAD.md`). Consúltalo antes de lanzar.
