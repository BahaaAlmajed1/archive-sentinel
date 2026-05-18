#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-}"
API_PORT="${API_PORT:-8080}"
UI_PORT="${UI_PORT:-5173}"
DB_NAME="${DB_NAME:-archive_sentinel}"
DB_USER="${DB_USER:-archive_sentinel}"
DB_PASSWORD="${DB_PASSWORD:-archive_sentinel}"
TDARR_URL="${TDARR_URL:-http://localhost:8266}"

usage() {
  cat <<USAGE
Archive Sentinel Linux setup

Usage:
  scripts/setup-linux.sh host
  scripts/setup-linux.sh docker

Environment overrides:
  API_PORT=8080
  UI_PORT=5173
  DB_NAME=archive_sentinel
  DB_USER=archive_sentinel
  DB_PASSWORD=archive_sentinel
  TDARR_URL=http://localhost:8266
USAGE
}

choose_mode() {
  if [[ -n "$MODE" ]]; then
    return
  fi
  echo "Choose setup mode:"
  echo "  1) host   - Java/Node/PostgreSQL run on this Linux host; use an existing Tdarr server"
  echo "  2) docker - Docker Compose runs API, UI, PostgreSQL, and managed Tdarr"
  read -r -p "Mode [host/docker]: " MODE
}

need_command() {
  command -v "$1" >/dev/null 2>&1
}

install_host_packages() {
  if need_command apt-get; then
    sudo apt-get update
    sudo apt-get install -y openjdk-21-jdk nodejs npm postgresql postgresql-contrib ffmpeg zenity curl
  elif need_command dnf; then
    sudo dnf install -y java-21-openjdk-devel nodejs npm postgresql-server postgresql-contrib ffmpeg zenity curl
    sudo postgresql-setup --initdb || true
  elif need_command yum; then
    sudo yum install -y java-21-openjdk-devel nodejs npm postgresql-server postgresql-contrib ffmpeg zenity curl
    sudo postgresql-setup initdb || true
  elif need_command pacman; then
    sudo pacman -Sy --needed jdk21-openjdk nodejs npm postgresql ffmpeg zenity curl
    sudo -iu postgres initdb -D /var/lib/postgres/data || true
  else
    echo "Unsupported package manager. Install Java 21, Node.js, npm, PostgreSQL, ffmpeg, and zenity, then rerun."
    exit 1
  fi
}

start_postgres() {
  if need_command systemctl; then
    sudo systemctl enable --now postgresql || sudo systemctl enable --now postgresql@17-main || true
  else
    sudo service postgresql start || true
  fi
}

setup_database() {
  sudo -u postgres psql <<SQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '$DB_USER') THEN
    CREATE ROLE $DB_USER LOGIN PASSWORD '$DB_PASSWORD';
  ELSE
    ALTER ROLE $DB_USER WITH LOGIN PASSWORD '$DB_PASSWORD';
  END IF;
END
\$\$;
SELECT 'CREATE DATABASE $DB_NAME OWNER $DB_USER'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$DB_NAME')\gexec
GRANT ALL PRIVILEGES ON DATABASE $DB_NAME TO $DB_USER;
SQL
}

write_env() {
  cat > "$ROOT_DIR/.env.local" <<ENV
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/$DB_NAME
SPRING_DATASOURCE_USERNAME=$DB_USER
SPRING_DATASOURCE_PASSWORD=$DB_PASSWORD
APP_EXECUTION_MODE=HOST_NATIVE
APP_TDARR_MODE=EXISTING
APP_TDARR_BASE_URL=$TDARR_URL
APP_ALLOWED_ORIGIN=http://localhost:$UI_PORT
SERVER_PORT=$API_PORT
VITE_API_PROXY_TARGET=http://localhost:$API_PORT
ENV
}

build_host_app() {
  (cd "$ROOT_DIR/backend" && ./gradlew bootJar)
  (cd "$ROOT_DIR/frontend" && npm install && npm run build)
}

start_host_app() {
  mkdir -p "$ROOT_DIR/runtime/logs"
  set -a
  # shellcheck disable=SC1091
  source "$ROOT_DIR/.env.local"
  set +a
  nohup "$ROOT_DIR/backend/gradlew" -p "$ROOT_DIR/backend" bootRun > "$ROOT_DIR/runtime/logs/backend.log" 2>&1 &
  nohup npm --prefix "$ROOT_DIR/frontend" run dev -- --host 0.0.0.0 --port "$UI_PORT" > "$ROOT_DIR/runtime/logs/frontend.log" 2>&1 &
  echo "Archive Sentinel is starting."
  echo "UI:  http://localhost:$UI_PORT"
  echo "API: http://localhost:$API_PORT"
  echo "Logs: $ROOT_DIR/runtime/logs"
}

setup_docker() {
  if ! need_command docker; then
    echo "Docker is not installed. Install Docker Engine and the Docker Compose plugin, then rerun this script."
    exit 1
  fi
  if ! docker compose version >/dev/null 2>&1; then
    echo "Docker Compose plugin is not available. Install docker compose, then rerun this script."
    exit 1
  fi
  (cd "$ROOT_DIR" && docker compose up -d --build)
  echo "Docker stack is starting."
  echo "UI: http://localhost:3000"
  echo "API: http://localhost:8080"
  echo "Tdarr UI: http://localhost:8265"
}

main() {
  choose_mode
  case "${MODE,,}" in
    host|native|host-native)
      install_host_packages
      start_postgres
      setup_database
      write_env
      build_host_app
      start_host_app
      ;;
    docker|compose)
      setup_docker
      ;;
    -h|--help|help)
      usage
      ;;
    *)
      usage
      exit 1
      ;;
  esac
}

main "$@"
