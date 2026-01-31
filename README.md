# StatusBeat

![Java](https://img.shields.io/badge/Java-25-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.7-brightgreen)
![MongoDB](https://img.shields.io/badge/MongoDB-7.0-green)
![Slack API](https://img.shields.io/badge/Slack-API-4A154B)
![Spotify API](https://img.shields.io/badge/Spotify-API-1DB954)
![License](https://img.shields.io/badge/license-MIT-blue)

Automatically sync your currently playing Spotify music with your Slack status.

![StatusBeat Screenshot](Screenshot%202026-01-31%20at%2013.49.38.png)

## Documentation

- [Setup Guide](SETUP_GUIDE.md) - Complete setup instructions
- [Commands Reference](COMMANDS.md) - All available slash commands

## Features

- **Auto-sync**: Updates your Slack status with currently playing Spotify tracks
- **Real-time**: Status updates every 10 seconds when music changes
- **Playback Controls**: Control Spotify playback directly from Slack
- **Secure**: OAuth tokens encrypted using AES-256
- **Customizable**: Configure emoji, status format, and visibility preferences

## Tech Stack

- **Backend**: Java 25 + Spring Boot 3.5.7
- **Database**: MongoDB
- **APIs**: Slack API, Spotify Web API
- **Security**: OAuth2, Spring Security, AES-256 encryption

## Prerequisites

- Java 25 or higher
- MongoDB (local or cloud instance)
- Slack workspace with admin access
- Spotify Premium account (required for playback control)

## Quick Start

### 1. Clone the Repository

```bash
git clone <repository-url>
cd statusbeat
```

### 2. Configure Environment

Create a `.env` file:

```bash
# Slack Configuration
SLACK_CLIENT_ID=your_slack_client_id
SLACK_CLIENT_SECRET=your_slack_client_secret
SLACK_SIGNING_SECRET=your_slack_signing_secret
SLACK_REDIRECT_URI=http://localhost:8080/oauth/slack/callback

# Spotify Configuration
SPOTIFY_CLIENT_ID=your_spotify_client_id
SPOTIFY_CLIENT_SECRET=your_spotify_client_secret
SPOTIFY_REDIRECT_URI=http://localhost:8080/oauth/spotify/callback

# MongoDB Configuration
SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/statusbeat

# Encryption (generate with: openssl rand -base64 32)
ENCRYPTION_SECRET_KEY=your_32_character_secret_key_here
```

### 3. Build and Run

```bash
./gradlew build
./gradlew bootRun
```

The application starts on `http://localhost:8080`.

## Usage

1. Visit `http://localhost:8080`
2. Click "Connect to Slack"
3. Authorize Slack access
4. Authorize Spotify access
5. Your status syncs automatically

### Slash Commands

| Command | Description |
|---------|-------------|
| `/statusbeat play` | Resume Spotify playback |
| `/statusbeat pause` | Pause Spotify playback |
| `/statusbeat status` | Show current sync status |
| `/statusbeat sync` | Manually trigger sync |
| `/statusbeat enable` | Enable automatic sync |
| `/statusbeat disable` | Disable automatic sync |
| `/statusbeat reconnect` | Reconnect Spotify account |
| `/statusbeat help` | Show help message |

## Configuration

### Status Template

Default format: `{title} - {artist}`

Available placeholders:
- `{title}` - Song title
- `{artist}` - Artist name

### Default Emoji

The default status emoji is `:musical_note:`. Change in `application.properties`:

```properties
statusbeat.sync.default-emoji=:headphones:
```

### Polling Interval

Default polling interval is 10 seconds:

```properties
statusbeat.sync.polling-interval=10000
```

## Architecture

```
src/main/java/com/statusbeat/statusbeat/
├── config/          # Configuration classes
├── controller/      # REST controllers
├── model/           # MongoDB entities
├── repository/      # Data access layer
├── service/         # Business logic
├── slack/           # Slack integrations
└── util/            # Utilities
```

## Security

- OAuth tokens encrypted using AES-256 with PBKDF2 key derivation
- HTTPS enforced in production
- CSRF protection enabled for web endpoints
- Never commit `.env` or credentials

## Testing

```bash
./gradlew test
./gradlew test jacocoTestReport  # With coverage
```

## Deployment

### Docker

```bash
docker build -t statusbeat .
docker-compose up -d
```

Update redirect URIs in Slack and Spotify apps to use your deployed URL.

## Troubleshooting

| Issue | Solution |
|-------|----------|
| MongoDB connection fails | Verify MongoDB is running and connection string is correct |
| Slack OAuth errors | Check redirect URI matches exactly in Slack app settings |
| Status not updating | Run `/statusbeat status` to verify sync is enabled |
| Home Tab not loading | Enable Event Subscriptions and subscribe to `app_home_opened` |

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/` | GET | Home page |
| `/oauth/slack` | GET | Initiate Slack OAuth |
| `/oauth/slack/callback` | GET | Slack OAuth callback |
| `/oauth/spotify` | GET | Initiate Spotify OAuth |
| `/oauth/spotify/callback` | GET | Spotify OAuth callback |
| `/slack/events` | POST | Slack events endpoint |
| `/health` | GET | Health check |

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## License

MIT License
