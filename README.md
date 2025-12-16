# Ninja Board - Quarkus Edition

This is a modernized Quarkus-based version of the Ninja Board application, migrated from the legacy JSP application.

## Overview

The Ninja Board is a points/leaderboard system for tracking contributions from team members. It integrates with various services (Trello, GitHub, GitLab, etc.) to gather statistics and award points to users based on their contributions.

## Features

- REST API for managing users, scorecards, and points
- Integration with Google Drive API for user registration
- Scheduled scripts to gather statistics from external services
- Leaderboard and charting capabilities
- User level/belt system (ZERO, BLUE, GREY, RED, BLACK)

## Prerequisites

- Java 17 or higher
- Maven 3.8+
- Access to Google Drive API (for user registration)
- API tokens for external services (Trello, GitHub, GitLab, etc.)

## Configuration

Configuration is done via environment variables and `application.properties`. Key configuration options:

### Environment Variables

```bash
export TRELLO_API_TOKEN=<your token>
export TRELLO_API_KEY=<your key>
export GITHUB_API_TOKEN=<your token>
export GITLAB_API_TOKEN=<your token>
export SMARTSHEETS_API_TOKEN=<your token>
export GD_CREDENTIALS=<google drive credentials JSON>
export GRAPHS_PROXY=<optional: url to external proxy storage>
export USERS_LDAP_PROVIDER=<optional: ldap url>
```

### Application Properties

See `src/main/resources/application.properties` for all configuration options.

## Building and Running

### Development Mode

```bash
mvn quarkus:dev
```

The application will be available at `http://localhost:8082`

### Production Build

```bash
mvn clean package
java -jar target/quarkus-app/quarkus-run.jar
```

## API Endpoints

### Scorecards
- `GET /api/scorecards` - Get all scorecards
- `GET /api/scorecard/{user}` - Get scorecard for a specific user
- `POST /api/scorecard/{user}` - Update scorecard for a user

### Charts/Leaderboard
- `GET /api/ninjas` - Get all ninjas (participants with belts)
- `GET /api/leaderboard/{max}` - Get leaderboard (limited to max entries)
- `GET /api/scorecard/breakdown/{user}` - Get point breakdown for a user
- `GET /api/scorecard/summary/{user}` - Get scorecard summary for a user
- `GET /api/scorecard/nextlevel/{user}` - Get points to next level chart data

### Configuration
- `GET /api/config/get` - Get current configuration
- `POST /api/config/save` - Save configuration

### Database
- `GET /api/database/get` - Get database contents
- `POST /api/database/save` - Save database contents

### Management
- `POST /api/yearEnd/{priorYear}` - Archive year-end data
- `GET /api/scripts/runNow` - Run scripts immediately
- `GET /api/scripts/publishGraphs` - Publish graph data

## Data Storage

Data is stored in JSON files:
- Config: `target/ninja-persistence/config.json`
- Database: `target/ninja-persistence/database2.json`

These paths can be configured via `application.properties`.

## Migration from JSP App

This Quarkus application maintains API compatibility with the original JSP application, so it can be used as a drop-in replacement. The main differences:

1. **Frontend**: Uses modern HTML/JavaScript instead of JSP
2. **Framework**: Quarkus instead of servlet/JSP
3. **Dependencies**: Updated to modern versions
4. **Configuration**: Uses MicroProfile Config instead of system properties

## TODO / Future Enhancements

- [ ] Implement Heartbeat2 service for scheduled script execution
- [ ] Implement GoogleDrive3 service for Google Sheets integration
- [ ] Implement ScriptRunner for executing Python scripts
- [ ] Add authentication/authorization
- [ ] Add more frontend pages (leaderboard, events, tasks, etc.)
- [ ] Implement export functionality (CSV, XLS, JSON)
- [ ] Add LDAP integration for user lookup

## License

Same as the original project.

