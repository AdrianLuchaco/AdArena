# Fase 7 — Avisos, emails y recuperar la contraseña

> **Estado:** ✅ terminada (2026-09-27)
> **Resultado verificable:**
> - La **campana** de la cabecera muestra los avisos sin leer; **Mi panel → Avisos** los lista.
> - Cada aviso llega **al instante** como aviso emergente (probado: "Recarga confirmada" apareció en la pantalla de Bicis Norte en cuanto el admin la confirmó).
> - Los emails se guardan en una bandeja de salida y se envían con reintentos. **En tu ordenador no se envía nada**: se escriben en el log del backend. En producción salen por SMTP (Resend u otro).
> - "¿Has olvidado tu contraseña?" en la pantalla de entrar.

---

## Índice

1. [Qué avisos hay](#1-qué-avisos-hay)
2. [Cómo viaja un aviso](#2-cómo-viaja-un-aviso)
3. ["Te han superado" sin llenar el buzón](#3-te-han-superado-sin-llenar-el-buzón)
4. [Los emails: bandeja de salida y reintentos](#4-los-emails-bandeja-de-salida-y-reintentos)
5. [Recuperar la contraseña](#5-recuperar-la-contraseña)
6. [La API](#6-la-api)
7. [La web](#7-la-web)
8. [Configurar el email en producción](#8-configurar-el-email-en-producción)
9. [Archivo por archivo](#9-archivo-por-archivo)
10. [Tests](#10-tests)
11. [Pendiente](#11-pendiente)

---

## 1. Qué avisos hay

| Aviso | Cuándo | Email |
|---|---|:---:|
| Te han superado en la Arena | Alguien te adelanta (regla 4) | ✅ (como mucho uno por cada puja tuya) |
| ¡Has ganado la Arena! | Cierre diario, si quedas 1.º | ✅ |
| La Arena ha terminado: conservas X € | Cierre diario, si no ganas | — (solo en la web, para no mandar un email diario) |
| ¡Ahora el ganador eres tú! | Se rechazó al 1.º y pasas a ser el candidato | ✅ |
| Tu anuncio ya está en portada | Lo apruebas | ✅ |
| Tu anuncio no se ha publicado (+ motivo) | Lo rechazas: se le devuelve el 100 % | ✅ |
| Te hemos devuelto tu puja | No se moderó a tiempo: 100 % devuelto | ✅ |
| Recarga confirmada | Confirmas su transferencia | ✅ |
| Recarga no completada (+ motivo) | Descartas su recarga | ✅ |

Todos los textos están en un solo archivo: `notification/service/Notices.java`.

## 2. Cómo viaja un aviso

Los avisos se crean **dentro de la misma transacción** que la operación que los provoca (la puja, el cierre, la moderación…). Esto es el patrón **outbox** ("bandeja de salida"):

1. Se guarda el aviso en `notifications` (la campana).
2. Si lleva email, se guarda en `email_outbox` (bandeja de salida).
3. **Después del commit**, se envía por WebSocket al canal privado del usuario (aviso emergente).
4. Unos segundos después, una tarea envía los emails pendientes.

Ventaja: si la operación falla y se deshace, **no queda ningún aviso ni email** de algo que no ha pasado. Y si el servidor de correo está caído, el email no se pierde: se reintenta.

## 3. "Te han superado" sin llenar el buzón

En una guerra de pujas, alguien podría recibir diez emails en un minuto. Para evitarlo, cada email "te han superado" lleva una **clave única**: `outbid:<tu participación>:<tu última puja>`.

- Te superan → recibes 1 email.
- Te supera otra persona más, sin que tú hayas vuelto a pujar → no recibes otro (misma clave).
- Vuelves a pujar y te superan otra vez → sí recibes otro (tu última puja ha cambiado).

La base de datos lo garantiza (`UNIQUE (dedup_key)` + `ON CONFLICT DO NOTHING`): un duplicado no es un error y **nunca** hace fallar la puja de la otra persona. El aviso emergente al instante, en cambio, llega siempre.

## 4. Los emails: bandeja de salida y reintentos

`OutboxEmailSender` (cada 10 segundos):
- Coge hasta 20 emails pendientes **bloqueados** con `FOR UPDATE SKIP LOCKED`: si hay dos copias del backend, cada una coge emails distintos y ninguno sale dos veces.
- Si el envío falla, reintenta a los 1, 2, 4 y 8 minutos. Tras 5 intentos lo marca `FAILED` y el panel de administración te lo cuenta.
- Sin servidor de correo configurado, los emails se escriben en el log (`[EMAIL NOT SENT: no SMTP configured]`) y se marcan como enviados. Así puedes ver en local qué se enviaría.

**Plantillas** (`EmailTemplates`): versión HTML (tarjeta blanca con el logo, título, texto y botón naranja) y versión de texto plano. Todo lo que escribe un usuario o el admin (nombres, motivos) se **escapa**: un nombre como `<script>` se muestra como texto y nunca se ejecuta (lo comprueba un test).

## 5. Recuperar la contraseña

1. En **Entrar** → "¿Has olvidado tu contraseña?" (`/recuperar`) escribes tu email.
2. La respuesta es **siempre la misma**, exista o no la cuenta. Así nadie puede usar este formulario para averiguar quién está registrado.
3. Si existe, te llega un enlace `…/restablecer?token=…`:
   - el token es aleatorio (32 bytes) y en la base de datos solo se guarda su **hash**;
   - caduca en **60 minutos** y sirve **una sola vez**;
   - como mucho **3 enlaces por hora** por cuenta, y 5 peticiones por hora por IP.
4. En `/restablecer` eliges la contraseña nueva. La web **quita el token de la barra de direcciones** nada más abrir la página (no queda en el historial) y la página no envía la dirección a otras webs (`referrer: no-referrer`).
5. Al cambiarla se **cierran todas tus sesiones** en todos los dispositivos y los demás enlaces pendientes dejan de servir.

## 6. La API

| Método | Ruta | Acceso | Qué hace |
|---|---|---|---|
| GET | `/api/me/notifications` | Sesión | Tus 50 avisos más recientes + cuántos sin leer |
| POST | `/api/me/notifications/read-all` | Sesión | Marcar todos como leídos |
| POST | `/api/me/notifications/{id}/read` | Sesión | Marcar uno (solo los tuyos: 404 si es de otro) |
| POST | `/api/auth/password/forgot` | Público | `{"email": "…"}` → siempre 202 |
| POST | `/api/auth/password/reset` | Público | `{"token": "…", "newPassword": "…"}` → 204, o 400 `RESET_TOKEN_INVALID` |
| WS | `/user/queue/notifications` | Sesión | Todos los avisos al instante |

## 7. La web

- **Campana** en la cabecera con el número de avisos sin leer (hasta "9+"). Lleva a `/panel/avisos`.
- **Mi panel → Avisos**: lista con icono por tipo, fecha y enlace a donde toca ("Volver a pujar", "Ver mi saldo"…). Al abrirla se marcan como leídos.
- **Avisos emergentes** para todos los tipos, con su color: verde (ganar, aprobado, recarga), naranja/rojo (te han superado, rechazado) o neutro.
- Al recibir un aviso, la tarjeta "Tu puja" y la pantalla de saldo se **actualizan solas**.
- **`/recuperar`** y **`/restablecer`**, con el mismo diseño que entrar y crear cuenta.

## 8. Configurar el email en producción

Recomiendo **Resend** (plan gratuito de 3.000 emails al mes). Pasos en `AUDITORIA-SEGURIDAD.md` → "Qué tienes que hacer tú". Variables:

| Variable | Ejemplo con Resend |
|---|---|
| `SMTP_HOST` | `smtp.resend.com` |
| `SMTP_PORT` | `465` |
| `SMTP_SSL` | `true` (para el puerto 587 pon `false`: usará STARTTLS) |
| `SMTP_USERNAME` | `resend` |
| `SMTP_PASSWORD` | tu API key (`re_…`) |
| `MAIL_FROM` | `AdArena <avisos@tudominio.com>` (dominio verificado en Resend) |
| `PUBLIC_URL` | `https://tudominio.com` (para los enlaces de los emails) |

⚠️ **Sin SMTP en producción, los enlaces de "recuperar contraseña" quedarían escritos en el log.** Solo tú ves el log de Railway, pero configura el SMTP antes de abrir la web al público.

## 9. Archivo por archivo

| Archivo | Nuevo/Mod. | Qué hace |
|---|---|---|
| `db/migration/V9__password_reset_and_notification_types.sql` | Nuevo | Tabla `password_reset_tokens`, dos tipos de aviso nuevos e índice para caducar huecos |
| `notification/service/NotificationService.java` | Nuevo | Crear avisos (con y sin email, con clave anti-duplicados), listarlos y marcarlos |
| `notification/service/Notices.java`, `Notice.java` | Nuevos | Los textos de todos los avisos |
| `notification/service/EmailTemplates.java` | Nuevo | Emails HTML + texto, con escapado |
| `notification/service/MailConfig.java`, `MailDelivery.java` | Nuevos | SMTP de verdad o, sin configurar, al log |
| `notification/service/OutboxEmailSender.java` | Nuevo | Envío con reintentos |
| `notification/repository/*` | Nuevos | Avisos y bandeja de salida (`ON CONFLICT DO NOTHING`, `SKIP LOCKED`) |
| `notification/controller/NotificationController.java`, `dto/NotificationsResponse.java` | Nuevos | API de la campana |
| `notification/event/UserNotificationEvent.java` | Nuevo | Aviso privado que se envía por WebSocket tras el commit |
| `notification/domain/NotificationType.java` | Mod. | `TOP_UP_REJECTED` y `CANDIDATE_PROMOTED` |
| `realtime/ArenaBroadcaster.java`, `ArenaNotification.java` | Mod. | Envía todos los avisos privados |
| `auction/service/BidService.java` | Mod. | Email "te han superado" (§3) |
| `user/service/PasswordResetService.java`, `domain/PasswordResetToken.java`, `repository/PasswordResetTokenRepository.java`, `dto/ForgotPasswordRequest.java`, `dto/ResetPasswordRequest.java` | Nuevos | Recuperar la contraseña |
| `user/repository/RefreshTokenRepository.java` | Mod. | Cerrar todas las sesiones de un usuario |
| `user/controller/AuthController.java` | Mod. | Rutas de contraseña |
| `common/text/Money.java` | Movido | Formato de euros compartido (antes en `auction/service`) |
| `pom.xml` | Mod. | `spring-boot-starter-mail` |
| `frontend/src/lib/arena-context.tsx` | Mod. | Avisos emergentes para todos los tipos y contador de no leídos |
| `frontend/src/components/panel/NotificationsView.tsx`, `app/panel/avisos/page.tsx` | Nuevos | Página de avisos |
| `frontend/src/components/layout/SiteHeader.tsx`, `panel/PanelShell.tsx` | Mod. | Campana y pestaña Avisos |
| `frontend/src/components/auth/ForgotPasswordForm.tsx`, `ResetPasswordForm.tsx`, `app/recuperar`, `app/restablecer` | Nuevos | Recuperar la contraseña |
| `frontend/src/components/auth/LoginForm.tsx` | Mod. | Enlace "¿Has olvidado tu contraseña?" |

## 10. Tests

| Clase | Tests | Qué comprueba |
|---|---:|---|
| `NotificationIntegrationTest` | 4 | Un email "te han superado" por puja (no uno por rival); campana: lista, no leídos, nadie toca los avisos de otro, marcar como leídos; la bandeja de salida se vacía y queda `SENT`; los nombres con HTML se escapan en los emails |
| `PasswordResetIntegrationTest` | 5 | Misma respuesta exista o no la cuenta; flujo completo leyendo el enlace del email (la contraseña vieja deja de valer, la sesión anterior se cierra, el enlace no sirve dos veces); enlaces caducados o inventados; máximo 3 por hora; longitud mínima |

## 11. Pendiente

- **Verificar el email al registrarse.** Hoy cualquiera puede registrarse con un email que no es suyo (no puede hacer nada con el dinero de otro, pero recibirías emails rebotados). Recomendable antes de un lanzamiento grande.
- Preferencias de avisos (por ejemplo, desactivar los emails "te han superado").
