# Auditoría de seguridad y funcionamiento — AdArena

> **Fecha:** 2026-09-27 · **Alcance:** todo el código (backend, frontend, base de datos, configuración y Docker), revisado a mano archivo por archivo, más pruebas automáticas y en el navegador.
> **Resumen:** encontré **1 fallo crítico de funcionamiento** (la Arena nunca se cerraba), **1 problema alto de seguridad en tu ordenador** (la base de datos local era accesible desde tu wifi) y **4 mejoras medias/bajas**. Todo está corregido y cubierto por tests. Lo que queda son riesgos conocidos y decisiones que dependen de ti (§4 y §5).
>
> **Actualización fase 10 (2026-09-27):** AdArena ahora **lee las webs** de los proyectos y las **muestra dentro** del visor. Es la parte más delicada añadida hasta ahora: la revisión está en [§7](#7-revisión-de-la-fase-10-leer-y-mostrar-webs-de-terceros).
>
> **Actualización fase 9 (Arena Points, 2026-09-27):** ya no hay dinero real. Revisé el código nuevo (ganar puntos, Créditos extra, promociones y AdSense): **1 mejora aplicada** (doble clic en "Visitar") y los riesgos nuevos están en [§6](#6-revisión-de-la-fase-9-arena-points). Las secciones de abajo están actualizadas a los puntos.

---

## Índice

1. [Hallazgos y correcciones](#1-hallazgos-y-correcciones)
2. [Lo que revisé y está bien](#2-lo-que-revisé-y-está-bien)
3. [Pruebas realizadas](#3-pruebas-realizadas)
4. [Riesgos que quedan (y por qué)](#4-riesgos-que-quedan-y-por-qué)
5. [Qué tienes que hacer tú](#5-qué-tienes-que-hacer-tú)
6. [Revisión de la fase 9 (Arena Points)](#6-revisión-de-la-fase-9-arena-points)
7. [Revisión de la fase 10 (leer y mostrar webs de terceros)](#7-revisión-de-la-fase-10-leer-y-mostrar-webs-de-terceros)
8. [Revisión de la fase 11 (web en inglés, rutas nuevas, modo ventana)](#8-revisión-de-la-fase-11-web-en-inglés-rutas-nuevas-modo-ventana)
9. [Revisión de la fase 12 (la nube gratis)](#9-revisión-de-la-fase-12-la-nube-gratis)

---

## 1. Hallazgos y correcciones

| # | Gravedad | Hallazgo | Riesgo | Corrección |
|---|---|---|---|---|
| 1 | 🔴 **Crítica** (funcionamiento y dinero) | La ronda diaria **nunca se cerraba**. Tu ronda del 27 seguía "abierta" 11 horas después de medianoche | Nadie ganaba, el dinero de todos quedaba reservado para siempre y la web se quedaba parada tras el primer día | Cierre diario automático completo ([FASE-06](fases/FASE-06.md)). Probado con tus datos: se cerró sola en 4 s |
| 2 | 🟠 **Alta** (tu ordenador) | PostgreSQL local escuchaba en **todas** las redes (`0.0.0.0:5432`) con usuario y contraseña `adarena` | Cualquiera en tu misma wifi (casa, cafetería, coworking) podía entrar en la base de datos: leer emails y hashes de contraseñas o cambiar saldos | `docker-compose.yml` publica los puertos solo en `127.0.0.1`. Se aplica la próxima vez que arranques `./dev.sh` |
| 3 | 🟡 Media | La API aceptaba cuerpos JSON de **cualquier tamaño** | Alguien podía enviar peticiones de cientos de MB para agotar la memoria del servidor y tirarlo | `RequestSizeLimitFilter`: máximo 64 KB por petición JSON (la más grande real ocupa ~3 KB). Las imágenes mantienen su límite de 5 MB |
| 4 | 🟡 Media | La web no tenía **Content Security Policy** | Si algún día hubiera un fallo XSS, el atacante podría cargar scripts de su web o enviar datos a su servidor | CSP en todas las páginas: solo se cargan scripts propios y solo se conecta con tu API. Además, HSTS (HTTPS obligatorio) en producción |
| 5 | 🟢 Baja | Las respuestas de la API no llevaban CSP, `Referrer-Policy` ni `Permissions-Policy` | Defensa en profundidad | CSP `default-src 'none'`, `Referrer-Policy: no-referrer` y `Permissions-Policy` en `/api/**` |
| 6 | 🟢 Baja | Las rutas que usan la cookie de sesión dependían solo de `SameSite` + CORS frente a CSRF | Defensa en profundidad | `OriginCheckFilter`: `/api/auth/**` rechaza peticiones con un `Origin` que no sea tu web |
| 7 | 🟢 Baja | Las tareas programadas compartían hilos con el WebSocket | Un envío de email lento podía retrasar otras tareas | Hilos propios (`adarena-jobs-N`) |
| 8 | Funcional | No existía "he olvidado mi contraseña" | Usuarios bloqueados para siempre | Implementado de forma segura ([FASE-07](fases/FASE-07.md) §5) |
| 9 | Funcional | No se podía recargar saldo, ni moderar al ganador, ni había avisos por email | La web no era usable en producción | Fases [5](fases/FASE-05.md), [7](fases/FASE-07.md) y [8](fases/FASE-08.md) (en la fase 9 las recargas se sustituyeron por los Arena Points) |
| 10 | Funcional | La pantalla de saldo no se actualizaba al confirmarse una recarga | Confusión del usuario | Se actualiza sola al recibir el aviso (hoy: "Mis puntos" y la cabecera) |

## 2. Lo que revisé y está bien

**Cuentas y sesiones**
- Contraseñas con **BCrypt**; se rechazan las de más de 72 bytes (BCrypt ignoraría el resto).
- El login tarda lo mismo exista o no el email (no se puede averiguar quién está registrado midiendo tiempos).
- El token de acceso (15 min) vive **solo en memoria**, nunca en `localStorage`.
- La sesión larga va en una cookie `HttpOnly; Secure; SameSite=Lax; Path=/api/auth`, que JavaScript no puede leer. Se **rota** en cada uso y, si alguien reutiliza una robada, se cierran todas las sesiones de esa familia.
- JWT firmados con HS256 y un secreto de al menos 32 bytes **obligatorio** en producción (sin él, el backend no arranca).
- El rol de administrador se comprueba **en el servidor**, en la URL y otra vez en cada método.
- La cuenta de admin solo la crea el arranque con `ADMIN_EMAIL`: nadie puede convertirse en admin registrándose antes con ese email.

**Puntos** (antes, dinero)
- Puntos **enteros** (nunca decimales con errores de redondeo).
- **Libro de partida doble** con redes de seguridad en PostgreSQL que funcionan aunque el código Java tuviera un fallo: cada movimiento suma cero, el historial no se puede editar ni borrar, ningún usuario puede quedar en negativo y la misma operación no se puede apuntar dos veces.
- **Bloqueos de filas** con un orden único en toda la aplicación (sin interbloqueos), probado con 20 y 40 pujas simultáneas.
- **Idempotencia**: doble clic o reintentos no pujan ni dan puntos dos veces.
- Los puntos solo los da el servidor (bienvenida, ticks y tareas con sus reglas); nunca el navegador.

**Datos que envían los usuarios**
- Consultas SQL siempre con parámetros (ninguna se construye pegando texto): sin inyección SQL.
- React muestra los textos como texto (no hay `dangerouslySetInnerHTML` en todo el proyecto) y los emails escapan todo el contenido de usuarios: sin XSS.
- Imágenes: se detecta el formato por el contenido, se rechazan las "bombas de descompresión" y se vuelven a codificar desde cero (sin metadatos ni contenido oculto).
- Webs de anunciantes y enlaces promocionados: solo `https://`, sin IPs, `localhost` ni `usuario:clave@` (y un `CHECK` en la base de datos). Se abren en pestaña nueva con `noopener noreferrer`.
- Redirecciones tras entrar (`?next=`): solo a rutas internas (sin redirecciones abiertas a webs de phishing).

**Infraestructura**
- **Límite de peticiones por IP**: login 10/min, registro 5/h, recuperar contraseña 5/h, pujas 60/min, promociones 30/h, subidas 30/h, rutas públicas 600/min, resto 300/min.
- WebSocket: solo desde tu web, token dentro de STOMP, solo dos canales permitidos y los clientes no pueden enviar mensajes.
- Los errores nunca muestran detalles internos al usuario.
- En producción: Swagger desactivado, Actuator solo expone `/health` y las rutas de desarrollo (puntos de prueba, "terminar la Arena") **no existen** (404, comprobado por tests).
- El contenedor Docker del backend se ejecuta con un usuario sin privilegios.
- **Dependencias:** `npm audit` → 0 vulnerabilidades. Backend en Spring Boot 4.1.1, con soporte vigente.
- **Secretos:** ninguno en el repositorio ni en su historial. Los de desarrollo solo existen en `application-dev.yml` (perfil local).

## 3. Pruebas realizadas

- **Backend: 257 tests, 0 fallos** (fase 10), contra PostgreSQL real en Docker (Testcontainers). Incluyen el cierre diario punto a punto, la moderación con los casos A y B de la documentación, ganar puntos y su antitrampas, Créditos extra, promociones y denuncias, avisos, recuperación de contraseña, pujas simultáneas y las protecciones de esta auditoría (`SecurityHardeningTest`).
- **Frontend:** `npm run lint`, `tsc` y `npm run build` sin errores (21 páginas).
- **En el navegador, con tus datos:** cierre automático, aprobación del ganador desde el panel, portada con el ganador, recarga pedida por un usuario y confirmada por el admin con aviso al instante, puja que cambia la clasificación en directo, ficha de proyecto, Ganadores, escritorio y móvil sin desbordamientos, y **ninguna violación de la CSP** en la consola.
- **Fase 9, en el navegador y contra la API** (con una base de datos aparte): 200 puntos al registrarse, contador de un proyecto hasta 100/100, pausa al ocultar la pestaña, `429 TICK_TOO_SOON` al pedir ticks sin esperar, Créditos extra con cobro automático al volver, promoción publicada y visible para el admin, puja con puntos, "Los puntos cuadran", `/ads.txt` y CSP con un ID de AdSense ficticio, y ningún bloque de anuncios en las páginas que dan puntos.

## 4. Riesgos que quedan (y por qué)

| Riesgo | Explicación | Recomendación |
|---|---|---|
| El registro revela si un email ya existe | Al registrarte con un email usado, la web lo dice. Es lo habitual (si no, el usuario no entendería por qué no puede registrarse) | Aceptable: limitado a 5 registros por hora e IP |
| Sin bloqueo por cuenta tras muchos intentos | Se limita por IP, pero un atacante con muchas IPs podría probar contraseñas contra una cuenta | Tu cuenta de admin debe tener una contraseña **larga y única**. Más adelante: verificación en dos pasos para el admin |
| El token de acceso dura 15 min tras salir | Es inherente a los JWT | Aceptable |
| No se verifica el email al registrarse | Alguien podría registrarse con un email que no es suyo | Recomendable antes de un lanzamiento grande |
| CSP con `'unsafe-inline'` en scripts | Next.js lo necesita salvo que cada página se genere en el servidor en cada visita | Aceptable; sigue bloqueando scripts de otras webs |
| Límite de peticiones en memoria | Con varias copias del backend, cada una cuenta por separado | Con una sola instancia (el plan) es correcto |
| ~~IP real detrás del proxy~~ | Resuelto en la fase 12: la web pasa la IP real con una clave compartida (ver §9) | — |
| Imágenes públicas por su identificador | Cualquiera con el enlace exacto (imposible de adivinar) puede verlas, también las subidas y no usadas | Aceptable; son imágenes de anuncios |
| Sin email configurado, los enlaces de "recuperar contraseña" quedan en el log | Solo tú ves los logs de Render | Configura Brevo antes de abrir al público (docs/DESPLIEGUE.md §4) |
| Legal | Sin dinero real, el riesgo de juego baja mucho. Quedan RGPD, términos, aviso legal (LSSI) y la fiscalidad de AdSense | **Abogado y gestor antes de lanzar** (§5). No vender puntos sin consultarlo |

## 5. Qué tienes que hacer tú

### Ahora mismo (para probarlo en tu ordenador)

1. **Reinicia `./dev.sh`** (Ctrl + C y otra vez `./dev.sh`): así la base de datos pasa a escuchar solo en tu ordenador (hallazgo 2).
2. Prueba el ciclo completo:
   - Entra como `admin@adarena.local` / `AdminAdArena2026!` → **Administración**.
   - Con otra cuenta (o en otra ventana de incógnito): gana puntos en **Gana puntos** (proyectos y Créditos extra) y publica una promoción en **Promocionar**.
   - Puja en **La Arena** y espera al cierre de medianoche (desde la fase 12 ya no existe el botón para cerrarla antes).
   - Aprueba o rechaza al ganador en **Moderación** y mira la portada.
3. Dime qué te parece el diseño nuevo (ganador, clasificación y fichas) para ajustar lo que quieras.
4. ~~Guarda el trabajo en Git~~ → hecho en la fase 12 (falta subirlo a GitHub: docs/DESPLIEGUE.md §2).

### Antes de lanzar (no es programación, pero es imprescindible)

5. **Abogado.** Que revise antes del lanzamiento:
   - los **Términos y la Política de Privacidad** (reescritos para los puntos, hoy son borradores) y el **aviso legal** (la LSSI obliga a publicar tu nombre o razón social, NIF, dirección y email);
   - que los puntos, tal y como están (gratis, sin valor, no canjeables), no son juego. **Antes de vender puntos o dar premios con valor**, vuelve a consultarlo (Ley 13/2011).
6. **Gestor.** Los ingresos de AdSense: alta como autónomo o empresa, IVA e IRPF.
7. **Cuenta bancaria de empresa o de autónomo** para cobrar AdSense. Nunca tu cuenta personal.
8. **Un dominio propio** (por ejemplo `adarena.com` o `adarena.es`). Desde la fase 12 la sesión ya funciona sin él; hace falta para AdSense y para que los emails no acaben en spam.
9. **Cuentas en los servicios** (todos tienen plan gratuito o muy barato para empezar):
   - **GitHub** (guardar el código),
   - **Supabase** (base de datos), **Render** (backend), **Vercel** (la web),
   - **Brevo** (emails), **UptimeRobot** (vigilancia),
   - **Google AdSense** (anuncios; pasos en [FASE-09 §15](fases/FASE-09.md#15-lo-que-tienes-que-hacer-tú)).

   Activa la **verificación en dos pasos** en todas, y también en tu banco y tu email.

### Al desplegar (fase 12: guía completa en `docs/DESPLIEGUE.md`)

10. Seguir `docs/DESPLIEGUE.md` paso a paso: `JWT_SECRET` lo genera Render; `PROXY_SECRET` igual en Render
    y Vercel; contraseña de admin larga y única (guárdala en un gestor de contraseñas).
11. **Nunca** pongas `SPRING_PROFILES_ACTIVE=dev` en la nube.
12. **Copias de seguridad:** el plan gratuito de Supabase no deja descargar las suyas: haz una semanal con
    `pg_dump` (DESPLIEGUE §10). El libro de movimientos es tu contabilidad.
13. **Dependabot** ya viene configurado (`.github/dependabot.yml`): GitHub te propondrá las actualizaciones
    de seguridad de las librerías.

### Cada día, cuando esté en marcha

14. **Por la mañana:** Administración → **Moderación** → aprobar o rechazar al ganador (cuanto antes, más horas en portada).
15. **Cuando haya denuncias:** Administración → **Promociones** → abrir el enlace y ocultarlo (con motivo) o volver a publicarlo.
16. **De vez en cuando:** Administración → **Resumen** → la comprobación de cuentas debe estar en verde y los emails sin errores.

## 6. Revisión de la fase 9 (Arena Points)

Revisé todo el código nuevo (`earn/`, la migración V10, las páginas de ganar puntos, Créditos extra,
Promocionar y AdSense) y lo probé en el navegador y contra la API.

### 6.1 Hallazgos

| # | Gravedad | Hallazgo | Corrección |
|---|---|---|---|
| 11 | 🟢 Baja (funcionamiento) | Un doble clic muy rápido en "Visitar" hacía que la segunda petición fallara con `409 DATA_CONFLICT` y el usuario veía un error, aunque la tarea sí había empezado | Inserción idempotente (`ON CONFLICT DO NOTHING`) + bloqueo de la fila, como en las visitas a proyectos. Test nuevo que lanza dos "Visitar" a la vez (falla con el código anterior, pasa con el nuevo) |

### 6.2 Lo que revisé y está bien

- **Nadie gana puntos más deprisa que el reloj del servidor.** Probado a mano: abrir un proyecto y pedir un tick al instante → `429 TICK_TOO_SOON`. Muchas pestañas o proyectos a la vez tampoco sirven: un tick cada 10 s por usuario, en total.
- **Todos los puntos salen del ledger** (`POINTS_ISSUED`) con clave de idempotencia: repetir una petición no los duplica, y "Los puntos cuadran" en el panel lo comprueba.
- **Límites en la base de datos**, no solo en Java: un contador por visitante, empresa y día (`UNIQUE`), nunca "verte a ti mismo" (`CHECK`), una denuncia por persona y tarea (`UNIQUE`), enlaces solo `https://` (`CHECK`).
- **Promociones:** solo su dueño las pausa, reanuda o borra (probado); máximo 5 activas y 30 publicaciones por hora e IP; títulos y descripciones saneados (una línea, sin caracteres de control) y mostrados siempre como texto.
- **Ficha pública de un proyecto** (`/api/public/projects/{id}`): solo muestra lo que ya es público en la Arena (nombre, descripción, web, imagen, puja). Los identificadores se codifican en la URL (`encodeURIComponent`) y la petición la hace el navegador, así que no hay SSRF.
- **Puntos de prueba** (`/api/dev/points`): solo existen con el perfil `dev`; en producción, 404 (test).
- **AdSense:** sin `NEXT_PUBLIC_ADSENSE_CLIENT` no se carga nada de Google; con él, solo en `/promote` (antes `/promocionar`) y, pequeño, bajo las clasificaciones. `/ads.txt` responde 404 si no está configurado.

### 6.3 Riesgos nuevos (y por qué se aceptan)

| Riesgo | Explicación | Recomendación |
|---|---|---|
| Cuentas múltiples para ganar más puntos | Una persona puede crear varias cuentas. Freno actual: 5 registros por hora e IP, y los puntos no se pueden pasar de una cuenta a otra | Verificar el email antes de poder ganar puntos (fase 10). Si se abusa, límite de cuentas por dispositivo o CAPTCHA |
| Bots que esperan 10 s de verdad | Un script paciente gana como una persona (100 pts por proyecto y día como mucho). El servidor lo limita pero no lo distingue | Aceptable: los puntos no tienen valor económico. Si pasa, CAPTCHA invisible (Cloudflare Turnstile) al empezar a ver un proyecto |
| El antitrampas del navegador se puede saltar | Todo lo que corre en el navegador se puede manipular | Por eso decide el servidor (§6.2). El del navegador evita el uso "normal" en segundo plano |
| CSP más abierta con AdSense | Con AdSense activado se admiten scripts, imágenes, iframes y conexiones `https:` de cualquier dominio, porque Google no admite listas de dominios | Aceptable: la CSP ya tenía `'unsafe-inline'`; React escapa todo el contenido de usuarios y no hay HTML de usuarios. Mejora futura: CSP con *nonce* (obliga a renderizar cada página en el servidor) |
| Enlaces promocionados maliciosos (phishing, malware) | Se publican al momento | Denuncias de los usuarios (3 → se ocultan solas) y tu moderación. Solo `https://` públicos |
| Cuenta de AdSense suspendida | Si Google detecta tráfico incentivado en páginas con anuncios | Anuncios solo en `/promote` y, pequeños, bajo las clasificaciones; **no** actives los anuncios automáticos |

## 7. Revisión de la fase 10 (leer y mostrar webs de terceros)

Dos cosas nuevas y delicadas: el **servidor** de AdArena descarga páginas de internet que eligen los
usuarios (para la presentación del ganador y el visor) y el **navegador** de los usuarios muestra
webs de terceros dentro de AdArena.

### 7.1 El servidor lee webs: protección SSRF

Riesgo: que alguien registre como "su web" una dirección que apunte a la red interna del servidor
(p. ej. `169.254.169.254`, los metadatos de la nube, que pueden contener credenciales) y que AdArena
la lea y la muestre. Protecciones (todas con tests):

| Protección | Detalle |
|---|---|
| Solo IP públicas | `PublicDnsResolver` resuelve el dominio y descarta cualquier IP interna, de enlace local, multicast, reservada o de documentación (IPv4 e IPv6, incluidas IPv4 escondidas en IPv6: mapeadas, NAT64, 6to4, Teredo). El cliente HTTP se conecta **solo** a esas IP ya comprobadas: el *DNS rebinding* no sirve |
| Solo https en el puerto 443 | Y sin IP literales, `localhost`, dominios internos ni `usuario:clave@`. Se comprueba al empezar y en **cada redirección** (máx. 5), que se siguen a mano |
| Límites de recursos | 5 s para conectar, 8 s sin datos, 1,5 MB de HTML (se corta), 5 MB por imagen (se aborta); la descompresión gzip se corta en esos límites; 2 lecturas a la vez y cola de 200 |
| Nada que pueda filtrar datos | Sin cookies, sin cabeceras de autenticación, sin reintentos; solo GET |
| Solo se guarda lo "de escaparate" | Nombre, titular, descripción, color, frases (texto limpio y recortado) e imágenes **re-codificadas** (nunca el archivo original). React lo muestra como texto |
| Sin abuso para "escanear" | Solo se leen direcciones guardadas en anuncios o promociones (ya validadas), como mucho una vez cada 2 minutos y normalmente cada 20 h |

### 7.2 El navegador muestra webs de terceros

| Riesgo | Protección |
|---|---|
| Que la web mostrada cambie la página de AdArena (p. ej. a una falsa de "inicia sesión") | Iframe con `sandbox` **sin** `allow-top-navigation`: no puede navegar la pestaña de AdArena |
| Que lea datos de AdArena | Es de otro origen: el navegador no le deja leer AdArena (cookies, almacenamiento, página). La sesión de AdArena está en memoria y en una cookie `HttpOnly` de la API |
| Que la ventana aparte controle AdArena (*tabnabbing*) | Se abre en blanco, se pone `opener = null` y después se carga la web: no tiene acceso a la pestaña de AdArena |
| Permisos del dispositivo | La cabecera `Permissions-Policy` de AdArena bloquea cámara, micrófono y ubicación también para los iframes |
| Contenido engañoso o peligroso dentro del visor | El dominio real se ve siempre en la barra; denuncias (3 → se oculta) y moderación; solo `https` |

La CSP de AdArena permite ahora `frame-src 'self' https:` (cualquier web https en el visor). Es
necesario para el visor; el aislamiento lo da el atributo `sandbox`.

### 7.3 Puntos

- **Reloj de atención único** por persona y **tramos limitados al tiempo real**: abrir muchas pestañas,
  combinar visitas y Créditos extra o pedir muchos tramos a la vez no da más puntos (tests).
- **Premio al ganador** con clave de idempotencia (`winner-bonus:<hueco>`): nunca dos veces, y sale del
  ledger como el resto (el panel sigue comprobando que "los puntos cuadran").

### 7.4 Riesgos que quedan

| Riesgo | Recomendación |
|---|---|
| En modo ventana no se puede comprobar que el usuario mira esa ventana: desde la fase 11 solo que la abrió desde AdArena y que no está en AdArena (ver §8) | Aceptable: límites diarios y reloj único. Si se abusa, CAPTCHA al empezar |
| Una web podría servir contenido distinto a AdArena que a sus visitantes (p. ej. al detectar `AdArenaBot`) | La presentación la revisa el admin antes de publicarla y se congela al aprobarla |
| Webs que se dejan mostrar en iframe pero contienen phishing | Denuncias y moderación; el dominio siempre visible. Mejora futura: lista de dominios bloqueados |
| Visitas incentivadas a webs con AdSense de terceros | Es responsabilidad de cada anunciante; se avisa en los términos |

## 8. Revisión de la fase 11 (web en inglés, rutas nuevas, modo ventana)

### 8.1 Hallazgos

| # | Hallazgo | Gravedad | Corrección |
|---|---|---|---|
| 1 | El modo "ventana aparte" no puntuaba en YouTube, X, Instagram… Esas webs envían `Cross-Origin-Opener-Policy` y el navegador corta el enlace con la ventana abierta (`ventana.closed` pasa a `true` al instante), así que el contador creía que la había cerrado | Funcional (no de seguridad) | El contador ya no mira `ventana.closed`: cuenta desde que la abres con el botón de AdArena y mientras estás fuera de AdArena; se para al volver o con "I'm done" (`useActiveTimer`, `SiteViewer`) |
| 2 | Con una base de datos local de antes, el backend en modo `dev` **no arrancaba**: al traducir los datos de ejemplo intentaba escribir una descripción larga en el título de una promoción (máx. 80) y PostgreSQL lo rechaza aunque no coincida ninguna fila | Funcional (solo en tu ordenador) | `DemoSitesBackfill.translate()` solo prueba traducciones que caben en cada columna; test `DemoDataSeederTest.translatesOldSpanishDemoDataWithoutBreakingOnLongTexts` (falla con el código anterior) |

### 8.2 Lo que revisé y está bien

- **Redirecciones de las rutas antiguas** (`/panel` → `/account`, `/proyecto/:id` → `/watch/:id`…): son
  rutas fijas de la propia web (308), sin parámetros que vengan del usuario → no abren una redirección
  a webs externas. `?next=` sigue validado por `safeNextPath` (solo rutas internas).
- **Enlaces de los emails** (recuperar contraseña, avisos): apuntan a las rutas nuevas; los emails antiguos
  con rutas en español siguen funcionando gracias a las redirecciones.
- **Términos**: la versión pasa a `2026-09-27-en`. Al cambiar la versión, quien se registra acepta la nueva;
  las cuentas existentes guardan la versión que aceptaron.
- **CSP y cabeceras**: sin cambios (`frame-src 'self' https:` para el visor, con `sandbox`).

### 8.3 Riesgos que quedan

| Riesgo | Recomendación |
|---|---|
| En modo ventana, alguien podría abrir la web y dejarla de fondo mientras hace otra cosa fuera de AdArena | Igual que antes: los límites diarios (100 por web, 10 enlaces) y el reloj único acotan lo que se puede ganar. Si se abusa: CAPTCHA al empezar o un "¿sigues ahí?" cada minuto |
| Los términos en inglés son una traducción del borrador | Que los revise el abogado (fase 12) |

## 9. Revisión de la fase 12 (la nube gratis)

### 9.1 Cambios de seguridad

| # | Riesgo | Corrección |
|---|---|---|
| 1 | **Supabase publica una API REST automática** sobre las tablas del esquema `public` (roles `anon` y `authenticated`). Sin cerrarla, alguien con la dirección del proyecto y su clave pública podría leer o cambiar tablas (emails, hashes, puntos) | Doble barrera: la migración **V12** quita todos los permisos de esos roles (también sobre las tablas futuras; comprobado con roles simulados) y la guía obliga a desactivar la Data API en el panel |
| 2 | Sin dominio, la cookie de sesión entre `*.vercel.app` y `*.onrender.com` sería de terceros | La web reenvía `/api/*` (`proxy.ts`): la cookie es propia de la web. Comprobado: entrar, recargar y seguir dentro |
| 3 | Detrás del reenvío, el backend vería la IP de Vercel: **todos los usuarios compartirían el límite** de intentos (un atacante podría bloquear el login de todos) | La web añade la IP real y una clave compartida (`PROXY_SECRET`, ≥16 caracteres, comparación en tiempo constante). Sin la clave, la cabecera se ignora (nadie puede inventarse una IP). Tests `RateLimitFilterTest` y prueba real: el visitante A queda limitado y el B no |
| 4 | Render gratuito bloquea SMTP | Emails por la API HTTPS de Brevo. La clave de Brevo solo desde las IP de Render (Brevo → Authorized IPs) |
| 5 | Herramientas de desarrollo (puntos de prueba, cerrar la Arena ya) | Borradas (backend y web). Ya solo existían en el perfil `dev`; los tests siguen comprobando que esas rutas dan 404 |

### 9.2 Lo que revisé y está bien

- **Secretos:** ninguno en el repositorio. `JWT_SECRET` lo genera Render; el resto va en los paneles.
  `.gitignore` excluye `.env*` (salvo las plantillas) y las notas locales (`.remember/`).
- **GitHub Actions:** solo permisos de lectura y ninguna entrada no fiable en los comandos.
- **Cabeceras y CSP:** sin cambios; `connect-src` incluye solo la propia web y el WebSocket del backend.
- **Memoria:** con 512 MB (límite del plan gratuito) el backend usa ~380 MB (probado en Docker con ese
  límite y 0,1 CPU).

### 9.3 Riesgos que quedan

| Riesgo | Recomendación |
|---|---|
| Vercel ve el tráfico de la API (hace de intermediario) | Es el mismo proveedor que sirve la web; aceptable |
| Emails desde un Gmail vía Brevo pueden ir a spam y son más fáciles de suplantar | Dominio propio con SPF/DKIM en Brevo (DESPLIEGUE §12) |
| Plan gratuito: si Render tiene un problema, no hay segunda copia | UptimeRobot te avisa por email; las copias semanales con `pg_dump` protegen los datos |

