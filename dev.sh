#!/usr/bin/env bash
# =====================================================================
# Arranca TODO AdArena en tu ordenador con un solo comando:
#
#   ./dev.sh
#
# 1. Base de datos PostgreSQL (Docker)
# 2. Backend / API (Spring Boot)      → http://localhost:8081
# 3. Web (Next.js)                    → http://localhost:3000  ← ESTA es la web
#
# Para pararlo todo: Ctrl + C
# Si dice que un puerto está ocupado (AdArena sigue abierto en otra terminal):  ./dev.sh stop
# Puertos distintos:  BACKEND_PORT=8082 FRONTEND_PORT=3001 ./dev.sh
# =====================================================================
set -euo pipefail
cd "$(dirname "$0")"

BACKEND_PORT="${BACKEND_PORT:-8081}"
FRONTEND_PORT="${FRONTEND_PORT:-3000}"
BOLD=$'\033[1m'; ORANGE=$'\033[38;5;208m'; RED=$'\033[31m'; RESET=$'\033[0m'

say() { echo "${ORANGE}${BOLD}▸${RESET} ${BOLD}$*${RESET}"; }
fail() { echo "${RED}${BOLD}✖ $*${RESET}" >&2; exit 1; }
port_busy() { lsof -iTCP:"$1" -sTCP:LISTEN -n -P >/dev/null 2>&1; }
# Qué programa ocupa un puerto (para que sepas qué se va a cerrar)
port_owner() { lsof -iTCP:"$1" -sTCP:LISTEN -n -P 2>/dev/null | awk 'NR==2 {print $1" (PID "$2")"}'; }
stop_port() {
  local pids
  pids=$(lsof -ti tcp:"$1" -sTCP:LISTEN 2>/dev/null || true)
  if [ -n "$pids" ]; then
    say "Cerrando $(port_owner "$1") en el puerto $1"
    kill $pids 2>/dev/null || true
  fi
}

# ./dev.sh stop → cierra lo que ocupe los puertos de AdArena (por ejemplo, una copia que se quedó abierta)
if [ "${1:-}" = "stop" ]; then
  stop_port "$BACKEND_PORT"
  stop_port "$FRONTEND_PORT"
  sleep 2
  if port_busy "$BACKEND_PORT" || port_busy "$FRONTEND_PORT"; then
    fail "Algo sigue ocupando los puertos. Cierra la terminal o el editor donde está abierto AdArena."
  fi
  say "Puertos $BACKEND_PORT y $FRONTEND_PORT libres. Ya puedes arrancar con ./dev.sh"
  exit 0
fi

# --- Comprobaciones previas ---
docker info >/dev/null 2>&1 || fail "Docker no está en marcha. Abre Docker Desktop y vuelve a intentarlo."
command -v java >/dev/null || fail "No encuentro Java 21. Instálalo (por ejemplo desde https://adoptium.net)."
command -v npm >/dev/null || fail "No encuentro Node.js. Instálalo desde https://nodejs.org."
port_busy "$BACKEND_PORT" && fail "El puerto $BACKEND_PORT está ocupado por $(port_owner "$BACKEND_PORT"). Seguramente AdArena sigue abierto en otra terminal o en tu editor. Para liberarlo: ./dev.sh stop"
port_busy "$FRONTEND_PORT" && fail "El puerto $FRONTEND_PORT está ocupado por $(port_owner "$FRONTEND_PORT"). Seguramente AdArena sigue abierto en otra terminal. Para liberarlo: ./dev.sh stop"

# Al salir (Ctrl + C), parar todo lo que hemos arrancado
cleanup() {
  echo
  say "Parando AdArena…"
  kill 0 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# Cambio de nombre Publifi → AdArena: la base de datos antigua ocupa el puerto 5432. Se para
# (sin borrar sus datos: el volumen "publifi_postgres-data" se conserva por si acaso).
if docker ps -q --filter "name=publifi-postgres-1" | grep -q .; then
  say "Parando la base de datos antigua (Publifi)…"
  docker compose -p publifi down >/dev/null 2>&1 || docker stop publifi-postgres-1 >/dev/null
fi

say "1/3 · Base de datos"
docker compose up -d postgres >/dev/null

if [ ! -d frontend/node_modules ]; then
  say "Instalando las dependencias de la web (solo la primera vez)…"
  (cd frontend && npm install --no-audit --no-fund)
fi

say "2/3 · Backend (la primera vez tarda un par de minutos en descargar lo necesario)"
(
  cd backend
  PORT="$BACKEND_PORT" \
  FRONTEND_ORIGINS="http://localhost:$FRONTEND_PORT" \
  SPRING_PROFILES_ACTIVE=dev \
  ./mvnw -q spring-boot:run
) &

for _ in $(seq 1 180); do
  curl -sf "http://localhost:$BACKEND_PORT/actuator/health" >/dev/null && break
  sleep 1
done
curl -sf "http://localhost:$BACKEND_PORT/actuator/health" >/dev/null || fail "El backend no ha arrancado. Revisa los mensajes de arriba."

say "3/3 · Web"
(
  cd frontend
  NEXT_PUBLIC_API_URL="http://localhost:$BACKEND_PORT" npx next dev --port "$FRONTEND_PORT"
) &

for _ in $(seq 1 60); do
  curl -sf -o /dev/null "http://localhost:$FRONTEND_PORT" && break
  sleep 1
done

echo
echo "${BOLD}✅ AdArena está en marcha${RESET}"
echo "   🌐 Web:        ${ORANGE}${BOLD}http://localhost:$FRONTEND_PORT${RESET}"
echo "   ⚙️  API:        http://localhost:$BACKEND_PORT  (Swagger: /swagger-ui.html)"
echo "   👤 Admin:      admin@adarena.local / AdminAdArena2026!"
echo "   👤 Anunciante: demo-cafe@adarena.local / DemoAdArena2026!"
echo "   Pulsa Ctrl + C para pararlo todo."
echo

# Abre la web en el navegador (NO_BROWSER=1 ./dev.sh para no abrirla)
if [ -z "${NO_BROWSER:-}" ] && command -v open >/dev/null; then open "http://localhost:$FRONTEND_PORT"; fi
wait
