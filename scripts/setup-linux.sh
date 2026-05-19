#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE=""
API_PORT="${API_PORT:-8080}"
UI_PORT="${UI_PORT:-5173}"
DB_NAME="${DB_NAME:-archive_sentinel}"
DB_USER="${DB_USER:-archive_sentinel}"
DB_PASSWORD="${DB_PASSWORD:-archive_sentinel}"
POSTGRES_URL="${POSTGRES_URL:-${SPRING_DATASOURCE_URL:-}}"
INSTALL_PACKAGES="${INSTALL_PACKAGES:-auto}"
TDARR_MODE="${TDARR_MODE:-existing}"
TDARR_URL="${TDARR_URL:-http://localhost:8266}"
VITE_DEV_PROXY_ORIGIN="${VITE_DEV_PROXY_ORIGIN:-http://localhost:$UI_PORT}"

usage() {
  cat <<USAGE
Archive Sentinel Linux setup

Usage:
  scripts/setup-linux.sh host [--tdarr-url URL] [--postgres-url JDBC_URL --postgres-user USER --postgres-password PASS]
  scripts/setup-linux.sh host --managed-tdarr
  scripts/setup-linux.sh docker

Environment overrides:
  API_PORT=8080
  UI_PORT=5173
  DB_NAME=archive_sentinel
  DB_USER=archive_sentinel
  DB_PASSWORD=archive_sentinel
  POSTGRES_URL=jdbc:postgresql://db-host:5432/archive_sentinel
  INSTALL_PACKAGES=auto|skip
  TDARR_MODE=existing|managed
  TDARR_URL=http://localhost:8266
  TDARR_GPU_WORKERS=1
  TDARR_CPU_WORKERS=0
  VITE_DEV_PROXY_ORIGIN=http://localhost:5173
USAGE
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      host|native|host-native|docker|compose)
        MODE="$1"
        shift
        ;;
      --tdarr-url)
        TDARR_URL="$2"
        TDARR_MODE="existing"
        shift 2
        ;;
      --managed-tdarr)
        TDARR_MODE="managed"
        TDARR_URL="http://localhost:8266"
        shift
        ;;
      --postgres-url|--db-url)
        POSTGRES_URL="$2"
        shift 2
        ;;
      --postgres-user|--db-user)
        DB_USER="$2"
        shift 2
        ;;
      --postgres-password|--db-password)
        DB_PASSWORD="$2"
        shift 2
        ;;
      --skip-packages)
        INSTALL_PACKAGES="skip"
        shift
        ;;
      -h|--help|help)
        MODE="help"
        shift
        ;;
      *)
        echo "Unknown argument: $1"
        usage
        exit 1
        ;;
    esac
  done
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

use_existing_postgres() {
  [[ -n "$POSTGRES_URL" ]]
}

java_21_available() {
  need_command java && java -version 2>&1 | grep -q 'version "21'
}

node_available() {
  if ! need_command node || ! need_command npm; then
    return 1
  fi
  local major
  major="$(node -v | sed -E 's/^v([0-9]+).*/\1/')"
  [[ "$major" -ge 22 ]]
}

install_host_packages() {
  if [[ "$INSTALL_PACKAGES" == "skip" ]]; then
    echo "Skipping package installation because INSTALL_PACKAGES=skip."
    return
  fi
  if need_command apt-get; then
    sudo apt-get update
    sudo DEBIAN_FRONTEND=noninteractive apt-get install -y ca-certificates curl gnupg
    local packages=()
    java_21_available || packages+=(openjdk-21-jdk)
    if ! node_available; then
      curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
      packages+=(nodejs)
    fi
    need_command ffmpeg || packages+=(ffmpeg)
    use_existing_postgres || packages+=(postgresql postgresql-contrib)
    if [[ "$TDARR_MODE" == "managed" ]] && (! need_command docker || ! docker compose version >/dev/null 2>&1); then
      packages+=(docker.io docker-compose-v2)
    fi
    if [[ "${#packages[@]}" -gt 0 ]]; then
      sudo DEBIAN_FRONTEND=noninteractive apt-get install -y "${packages[@]}"
    fi
  elif need_command dnf; then
    local packages=(curl)
    java_21_available || packages+=(java-21-openjdk-devel)
    node_available || packages+=(nodejs npm)
    need_command ffmpeg || packages+=(ffmpeg)
    use_existing_postgres || packages+=(postgresql-server postgresql-contrib)
    sudo dnf install -y "${packages[@]}"
    use_existing_postgres || sudo postgresql-setup --initdb || true
  elif need_command yum; then
    local packages=(curl)
    java_21_available || packages+=(java-21-openjdk-devel)
    node_available || packages+=(nodejs npm)
    need_command ffmpeg || packages+=(ffmpeg)
    use_existing_postgres || packages+=(postgresql-server postgresql-contrib)
    sudo yum install -y "${packages[@]}"
    use_existing_postgres || sudo postgresql-setup initdb || true
  elif need_command pacman; then
    local packages=(curl)
    java_21_available || packages+=(jdk21-openjdk)
    node_available || packages+=(nodejs npm)
    need_command ffmpeg || packages+=(ffmpeg)
    use_existing_postgres || packages+=(postgresql)
    sudo pacman -Sy --needed "${packages[@]}"
    use_existing_postgres || sudo -iu postgres initdb -D /var/lib/postgres/data || true
  else
    echo "Unsupported package manager. Install Java 21, Node.js 22/npm, PostgreSQL if local, ffmpeg, and Docker if using managed Tdarr, then rerun with INSTALL_PACKAGES=skip."
    exit 1
  fi
}

start_postgres() {
  if use_existing_postgres; then
    echo "Using existing PostgreSQL: $POSTGRES_URL"
    return
  fi
  if need_command systemctl; then
    sudo systemctl enable --now postgresql || sudo systemctl enable --now postgresql@17-main || true
  else
    sudo service postgresql start || true
  fi
}

setup_database() {
  if use_existing_postgres; then
    echo "Skipping local database creation because POSTGRES_URL was provided."
    return
  fi
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
  local host_ip="${HOST_IP:-}"
  if [[ -z "$host_ip" ]] && need_command hostname; then
    host_ip="$(hostname -I 2>/dev/null | awk '{print $1}' || true)"
  fi
  local allowed_origins="http://localhost:$UI_PORT,http://127.0.0.1:$UI_PORT"
  if [[ -n "$host_ip" ]]; then
    allowed_origins="$allowed_origins,http://$host_ip:$UI_PORT"
  fi
  local datasource_url="${POSTGRES_URL:-jdbc:postgresql://localhost:5432/$DB_NAME}"
  local app_tdarr_mode="EXISTING"
  local managed_tdarr_enabled="false"
  if [[ "$TDARR_MODE" == "managed" ]]; then
    app_tdarr_mode="MANAGED"
    managed_tdarr_enabled="true"
  fi
  cat > "$ROOT_DIR/.env.local" <<ENV
SPRING_DATASOURCE_URL=$datasource_url
SPRING_DATASOURCE_USERNAME=$DB_USER
SPRING_DATASOURCE_PASSWORD=$DB_PASSWORD
APP_EXECUTION_MODE=HOST_NATIVE
APP_TDARR_MODE=$app_tdarr_mode
APP_TDARR_BASE_URL=$TDARR_URL
APP_MANAGED_TDARR_ENABLED=$managed_tdarr_enabled
APP_ALLOWED_ORIGINS=$allowed_origins
SERVER_PORT=$API_PORT
VITE_API_PROXY_TARGET=http://localhost:$API_PORT
VITE_DEV_PROXY_ORIGIN=$VITE_DEV_PROXY_ORIGIN
ENV
}

ensure_docker() {
  if ! need_command docker; then
    echo "Docker is required for managed Tdarr. Install Docker or rerun with --tdarr-url http://host:8266."
    exit 1
  fi
  if ! docker compose version >/dev/null 2>&1 && ! sudo docker compose version >/dev/null 2>&1; then
    echo "Docker Compose is required for managed Tdarr. Install the Docker Compose plugin or use an existing Tdarr URL."
    exit 1
  fi
}

setup_managed_tdarr() {
  [[ "$TDARR_MODE" == "managed" ]] || return 0
  ensure_docker
  local tdarr_dir="$ROOT_DIR/runtime/managed-tdarr"
  mkdir -p "$tdarr_dir"/{server,configs,logs,node}
  local gpu_line=""
  local gpu_env=""
  local dri_devices=""
  local gpu_present="false"
  if need_command nvidia-smi; then
    gpu_present="true"
    gpu_line="    gpus: all"
    gpu_env="
      NVIDIA_VISIBLE_DEVICES: all
      NVIDIA_DRIVER_CAPABILITIES: compute,video,utility"
  fi
  if [[ -d /dev/dri ]]; then
    gpu_present="true"
    dri_devices="    devices:
      - /dev/dri:/dev/dri"
  fi
  local default_gpu_workers="0"
  local default_cpu_workers="1"
  if [[ "$gpu_present" == "true" ]]; then
    default_gpu_workers="1"
    default_cpu_workers="0"
  fi
  local transcode_gpu_workers="${TDARR_TRANSCODE_GPU_WORKERS:-${TDARR_GPU_WORKERS:-$default_gpu_workers}}"
  local transcode_cpu_workers="${TDARR_TRANSCODE_CPU_WORKERS:-${TDARR_CPU_WORKERS:-$default_cpu_workers}}"
  local healthcheck_gpu_workers="${TDARR_HEALTHCHECK_GPU_WORKERS:-${TDARR_GPU_WORKERS:-$default_gpu_workers}}"
  local healthcheck_cpu_workers="${TDARR_HEALTHCHECK_CPU_WORKERS:-${TDARR_CPU_WORKERS:-$default_cpu_workers}}"
  cat > "$tdarr_dir/compose.yml" <<YAML
name: archive-sentinel-managed-tdarr
services:
  tdarr-server:
    image: ghcr.io/haveagitgat/tdarr:latest
    environment:
      serverIP: 0.0.0.0
      serverPort: 8266
      webUIPort: 8265
    volumes:
      - ./server:/app/server
      - ./configs:/app/configs
      - ./logs:/app/logs
      - /home:/home
      - /mnt:/mnt
      - /media:/media
      - /tmp:/tmp
      - $ROOT_DIR:$ROOT_DIR
    ports:
      - "8265:8265"
      - "8266:8266"
  tdarr-node:
    image: ghcr.io/haveagitgat/tdarr_node:latest
    environment:
      serverURL: http://tdarr-server:8266
      nodeType: mapped
      priority: 0
      nodeName: archive-sentinel-managed-node$gpu_env
      transcodegpuWorkers: "$transcode_gpu_workers"
      transcodecpuWorkers: "$transcode_cpu_workers"
      healthcheckgpuWorkers: "$healthcheck_gpu_workers"
      healthcheckcpuWorkers: "$healthcheck_cpu_workers"
$gpu_line
$dri_devices
    depends_on:
      - tdarr-server
    volumes:
      - ./node:/app/configs
      - ./logs:/app/logs
      - /home:/home
      - /mnt:/mnt
      - /media:/media
      - /tmp:/tmp
      - $ROOT_DIR:$ROOT_DIR
YAML
  if docker compose version >/dev/null 2>&1; then
    docker compose -f "$tdarr_dir/compose.yml" up -d
  else
    sudo docker compose -f "$tdarr_dir/compose.yml" up -d
  fi
  echo "Managed Tdarr is starting at http://localhost:8266; UI: http://localhost:8265"
}

build_host_app() {
  chmod +x "$ROOT_DIR/backend/gradlew"
  (cd "$ROOT_DIR/backend" && ./gradlew bootJar)
  (cd "$ROOT_DIR/frontend" && npm ci && npm run build)
}

start_detached() {
  local log_file="$1"
  shift
  if need_command setsid; then
    nohup setsid "$@" > "$log_file" 2>&1 < /dev/null &
  else
    nohup "$@" > "$log_file" 2>&1 < /dev/null &
  fi
}

start_host_app() {
  mkdir -p "$ROOT_DIR/runtime/logs"
  chmod +x "$ROOT_DIR/backend/gradlew"
  set -a
  # shellcheck disable=SC1091
  source "$ROOT_DIR/.env.local"
  set +a
  start_detached "$ROOT_DIR/runtime/logs/backend.log" "$ROOT_DIR/backend/gradlew" -p "$ROOT_DIR/backend" bootRun
  start_detached "$ROOT_DIR/runtime/logs/frontend.log" npm --prefix "$ROOT_DIR/frontend" run dev -- --host 0.0.0.0 --port "$UI_PORT"
  local host_ip="${HOST_IP:-}"
  if [[ -z "$host_ip" ]] && need_command hostname; then
    host_ip="$(hostname -I 2>/dev/null | awk '{print $1}' || true)"
  fi
  echo "Archive Sentinel is starting."
  echo "UI:  http://localhost:$UI_PORT"
  if [[ -n "$host_ip" ]]; then
    echo "LAN UI: http://$host_ip:$UI_PORT"
  fi
  echo "API: http://localhost:$API_PORT"
  echo "Tdarr: $TDARR_URL"
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
  parse_args "$@"
  choose_mode
  case "${MODE,,}" in
    host|native|host-native)
      install_host_packages
      start_postgres
      setup_database
      setup_managed_tdarr
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
