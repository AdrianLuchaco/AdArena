# Publifi

Cada día, una subasta decide qué anunciante ocupa la web durante las siguientes 24 horas.

- `backend/` — API en Java 21 + Spring Boot 3.5 (Maven, PostgreSQL, Flyway)
- `frontend/` — Web en Next.js + TypeScript + Tailwind
- `docs/ARCHITECTURE.md` — decisiones, reglas de negocio, modelo de datos y estructura

## Requisitos

- Java 21, Maven 3.9+
- Node.js 20+
- Docker Desktop (en marcha)

## Tests del backend

```bash
cd backend
mvn test
```

Los tests arrancan un PostgreSQL real en Docker (Testcontainers), aplican las migraciones y
comprueban el modelo de datos. La primera vez tardan más porque descargan la imagen.

## Frontend (de momento, la plantilla inicial)

```bash
cd frontend
npm run dev    # http://localhost:3000
```
