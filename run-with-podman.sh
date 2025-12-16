#!/bin/bash

# Script to run Quarkus application using Podman containers
# No Maven or Java installation required on the host

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$SCRIPT_DIR"
MAVEN_IMAGE="maven:3.9-eclipse-temurin-17"
JAVA_IMAGE="eclipse-temurin:17-jre"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

echo_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

echo_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if podman is available
if ! command -v podman &> /dev/null; then
    echo_error "Podman is not installed. Please install podman first."
    exit 1
fi

# Function to build the application
build_app() {
    echo_info "Building Quarkus application using Maven container..."
    
    podman run --rm \
        -v "$APP_DIR":/workspace:Z \
        -w /workspace \
        -e MAVEN_OPTS="-Dmaven.repo.local=/workspace/.m2/repository" \
        "$MAVEN_IMAGE" \
        mvn clean package -DskipTests
    
    if [ $? -eq 0 ]; then
        echo_info "Build completed successfully!"
    else
        echo_error "Build failed!"
        exit 1
    fi
}

# Function to run in dev mode
run_dev() {
    echo_info "Starting Quarkus in dev mode..."
    echo_info "Application will be available at http://localhost:8082"
    echo_info "Press Ctrl+C to stop"
    echo_warn "Note: Environment variables from your shell will be passed to the container"
    
    # Build environment variable arguments from current environment
    ENV_ARGS=()
    for var in TRELLO_API_TOKEN TRELLO_API_KEY GITHUB_API_TOKEN GITLAB_API_TOKEN \
               SMARTSHEETS_API_TOKEN GD_CREDENTIALS GRAPHS_PROXY USERS_LDAP_PROVIDER \
               LDAP_ENABLED LDAP_BASE_DN HEARTBEAT_INTERVAL HEARTBEAT_START_TIME \
               EVENTS_MAX LOGIN_ENABLED NINJA_STORAGE_ROOT; do
        if [ -n "${!var}" ]; then
            ENV_ARGS+=(-e "$var=${!var}")
        fi
    done
    
    podman run --rm -it \
        -v "$APP_DIR":/workspace:Z \
        -w /workspace \
        -p 8082:8082 \
        -e MAVEN_OPTS="-Dmaven.repo.local=/workspace/.m2/repository" \
        "${ENV_ARGS[@]}" \
        "$MAVEN_IMAGE" \
        mvn quarkus:dev
}

# Function to run the built JAR
run_prod() {
    if [ ! -f "$APP_DIR/target/quarkus-app/quarkus-run.jar" ]; then
        echo_warn "Application not built yet. Building now..."
        build_app
    fi
    
    echo_info "Starting Quarkus application in production mode..."
    echo_info "Application will be available at http://localhost:8082"
    echo_info "Press Ctrl+C to stop"
    
    # Create a temporary Dockerfile for running the JAR
    cat > "$APP_DIR/.Dockerfile.run" << 'EOF'
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/quarkus-app /app
EXPOSE 8082
CMD ["java", "-jar", "quarkus-run.jar"]
EOF
    
    podman build -f "$APP_DIR/.Dockerfile.run" -t ninja-board-quarkus:latest "$APP_DIR"
    
    # Build environment variable arguments from current environment
    ENV_ARGS=()
    for var in TRELLO_API_TOKEN TRELLO_API_KEY GITHUB_API_TOKEN GITLAB_API_TOKEN \
               SMARTSHEETS_API_TOKEN GD_CREDENTIALS GRAPHS_PROXY USERS_LDAP_PROVIDER \
               LDAP_ENABLED LDAP_BASE_DN HEARTBEAT_INTERVAL HEARTBEAT_START_TIME \
               EVENTS_MAX LOGIN_ENABLED NINJA_STORAGE_ROOT; do
        if [ -n "${!var}" ]; then
            ENV_ARGS+=(-e "$var=${!var}")
        fi
    done
    
    podman run --rm -it \
        -p 8082:8082 \
        -v "$APP_DIR/target/ninja-persistence":/app/target/ninja-persistence:Z \
        "${ENV_ARGS[@]}" \
        ninja-board-quarkus:latest
    
    # Cleanup
    rm -f "$APP_DIR/.Dockerfile.run"
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [command]"
    echo ""
    echo "Commands:"
    echo "  build    - Build the application using Maven container"
    echo "  dev      - Run the application in dev mode (default)"
    echo "  prod     - Build and run the application in production mode"
    echo "  clean    - Clean build artifacts"
    echo "  help     - Show this help message"
    echo ""
}

# Function to clean build artifacts
clean() {
    echo_info "Cleaning build artifacts..."
    podman run --rm \
        -v "$APP_DIR":/workspace:Z \
        -w /workspace \
        "$MAVEN_IMAGE" \
        mvn clean
    
    echo_info "Clean completed!"
}

# Main script logic
COMMAND="${1:-dev}"

case "$COMMAND" in
    build)
        build_app
        ;;
    dev)
        run_dev
        ;;
    prod)
        run_prod
        ;;
    clean)
        clean
        ;;
    help|--help|-h)
        show_usage
        ;;
    *)
        echo_error "Unknown command: $COMMAND"
        show_usage
        exit 1
        ;;
esac

