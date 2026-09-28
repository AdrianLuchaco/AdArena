# AdArena

Cada día, los proyectos compiten en **la Arena** por la portada de la web, pujando con **Arena Points**: puntos gratuitos que se ganan dentro de la web (no son dinero). Quien más ha pujado a medianoche aparece a pantalla completa durante las siguientes 24 horas.

La web está **en inglés** (desde la fase 11); el código, los comentarios y la documentación siguen en español.

- `frontend/`: **la web** (Next.js + TypeScript + Tailwind)
- `backend/`: la API (Java 21 + Spring Boot 4, PostgreSQL, Flyway, Spring Security + JWT, WebSocket)
- `docs/ARCHITECTURE.md`: decisiones, reglas de negocio, modelo de datos y estructura
- `docs/AUDITORIA-SEGURIDAD.md`: auditoría de seguridad y **qué tienes que hacer tú**
- `docs/fases/`: explicación completa de cada fase ([1](docs/fases/FASE-01.md) · [2](docs/fases/FASE-02.md) · [3](docs/fases/FASE-03.md) · [4](docs/fases/FASE-04.md) · [5](docs/fases/FASE-05.md) · [6](docs/fases/FASE-06.md) · [7](docs/fases/FASE-07.md) · [8](docs/fases/FASE-08.md) · [9](docs/fases/FASE-09.md) · [10](docs/fases/FASE-10.md) · [11](docs/fases/FASE-11.md) · [12](docs/fases/FASE-12.md))

## Qué hace

- **La Arena:** pujas que se suman, clasificación en directo, cuenta atrás y pujas de última hora que alargan el contador.
- **Cierre diario automático** a medianoche (Madrid): gana el 1.º; los demás conservan el 50 % para el día siguiente.
- **Moderación:** el anuncio ganador sale en portada cuando lo apruebas. Si lo rechazas, recupera el 100 % de sus puntos y pasa el siguiente.
- **Presentación animada del ganador:** su web se lee sola (logo, color, titular, frases y fotos) y la portada se convierte en una "película" de 5 escenas con su color de marca.
- **Gana puntos mirando webs:** 200 al registrarte y, en un visor a pantalla completa, 10 cada 10 s mirando la web de cada proyecto (dentro de AdArena o en su ventana), bonus a los 60 s y hasta 100 al día por proyecto, con antitrampas. También con los **Bonus links** (mirar enlaces que promocionan otros usuarios). Quien gana la Arena recibe 500.
- **Promote:** cualquiera publica gratis sus redes o su web para que aparezcan en los Bonus links. Anuncios de **Ezoic** (opcionales, se activan con una variable) aquí, en la portada y bajo las clasificaciones; nunca donde se ganan puntos.
- **Guía «How it works»** con todo explicado paso a paso.
- **Avisos** en la web, al instante y por email ("te han superado", "has ganado"…).
- **Panel de administración:** resumen de los puntos, moderación de anuncios y de promociones, ajustes y registro de acciones.

## Requisitos

- Docker Desktop (abierto)
- Java 21
- Node.js 20 o superior

No hace falta instalar Maven: el backend incluye el Maven Wrapper (`./mvnw`).

## Arrancarlo todo con un comando

```bash
./dev.sh
```

Se abre la web en **http://localhost:3000**. Para pararlo todo: `Ctrl + C`.

Si al arrancar te dice que el puerto 8081 o el 3000 **está ocupado** (normalmente porque AdArena se quedó abierto en otra terminal), libéralos con:

```bash
./dev.sh stop
```

| Qué | Dirección |
|---|---|
| 🌐 **La web** | http://localhost:3000 |
| 🛠️ Administración | http://localhost:3000/admin (con la cuenta de admin) |
| ⚙️ API (backend) | http://localhost:8081 (Swagger: `/swagger-ui.html`) |

Cuentas de prueba (solo en local):

| Cuenta | Email | Contraseña |
|---|---|---|
| Admin | `admin@adarena.local` | `AdminAdArena2026!` |
| Anunciantes de ejemplo | `demo-cafe@adarena.local`, `demo-bicis@…`, `demo-lumen@…`, `demo-huerta@…` | `DemoAdArena2026!` |

**En local:**
- Los emails no se envían: se escriben en la consola del backend (busca `[EMAIL NOT SENT`).
- Los datos de ejemplo (4 anunciantes, una Arena en marcha y un ganador) solo existen en tu ordenador.
- Los anunciantes de ejemplo tienen "webs de ejemplo" (logo, fotos y frases) para ver la presentación animada. Con tu propio anuncio, AdArena lee tu web de verdad.

## Tests

```bash
cd backend && ./mvnw test                            # 264 tests del backend (PostgreSQL real en Docker)
cd frontend && npm run lint && npm run build         # comprobaciones de la web
```

## Base de datos local

```bash
docker compose down      # parar (los datos se conservan)
docker compose down -v   # parar y BORRAR la base de datos local; al volver a arrancar se regeneran los datos de ejemplo
```

La base de datos local solo escucha en tu ordenador (`127.0.0.1`), no en tu red.

## Subirlo a internet (gratis)

La guía paso a paso está en **[docs/DESPLIEGUE.md](docs/DESPLIEGUE.md)**: GitHub, base de datos (Supabase), backend (Render), web (Vercel), emails (Brevo) y aviso si se cae (UptimeRobot). Todo con planes gratuitos, más un dominio en Cloudflare (≈ 10 $/año) y email con tu dominio (gratis).


Las variables de entorno del backend están explicadas en [`.env.example`](.env.example), y las de la web en [`frontend/.env.example`](frontend/.env.example). El despliegue paso a paso está en [docs/DESPLIEGUE.md](docs/DESPLIEGUE.md). Para activar los anuncios, mira [FASE-09 §15](docs/fases/FASE-09.md#15-lo-que-tienes-que-hacer-tú).
