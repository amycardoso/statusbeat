# 🎵 StatusBeat - Music-Slack Status Sync App

![Java](https://img.shields.io/badge/Java-25-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.7-brightgreen)
![MongoDB](https://img.shields.io/badge/MongoDB-7.0-green)
![Slack API](https://img.shields.io/badge/Slack-API-4A154B)
![Spotify API](https://img.shields.io/badge/Spotify-API-1DB954)
![License](https://img.shields.io/badge/license-MIT-blue)

StatusBeat automatically syncs your currently playing Spotify music with your Slack status, allowing you to share what you're listening to with your team in real-time.

## 📚 Documentation

- **[Setup Guide](SETUP_GUIDE.md)** - Complete setup instructions
- **[Commands Reference](COMMANDS.md)** - All available slash commands

## ✨ Features

- 🎧 **Auto-sync**: Automatically updates your Slack status with currently playing Spotify tracks
- ⚡ **Real-time**: Status updates every 10 seconds when your music changes
- 🎮 **Playback Controls**: Control Spotify playback directly from Slack using slash commands
- 🔒 **Secure**: OAuth tokens encrypted using AES-256 encryption
- ✨ **Customizable**: Configure emoji, status format, and visibility preferences
- 📊 **User Settings**: Enable/disable sync, customize status templates

## 🛠️ Tech Stack

- **Backend**: Java 25 + Spring Boot 3.5.7
- **Database**: MongoDB
- **APIs**: Slack API, Spotify Web API
- **Security**: OAuth2, Spring Security, AES-256 encryption

## 📋 Prerequisites

- Java 25 or higher
- MongoDB (local or cloud instance)
- Slack workspace with admin access
- Spotify Premium account (required for playback control)

## 🚀 Setup Instructions

### 1. Clone the Repository

```bash
git clone <repository-url>
cd statusbeat
```

### 2. Configure Slack App

1. Go to [Slack API Console](https://api.slack.com/apps)
2. Click "Create New App" → "From scratch"
3. Name it "StatusBeat" and select your workspace
4. Navigate to "OAuth & Permissions":
   - Add Redirect URL: `http://localhost:8080/oauth/slack/callback`
   - **IMPORTANT**: Add **User Token Scopes** (NOT Bot Token Scopes):
     - `users.profile:write` (allows updating user's own status)
     - `users.profile:read` (allows reading user's profile)
   - Optional Bot Token Scopes:
     - `chat:write` (optional - for sending messages)
5. Navigate to "Slash Commands" and create a new command:
   - Command: `/statusbeat`
   - Request URL: `http://localhost:8080/slack/events`
   - Description: "Control StatusBeat music sync"
6. Navigate to "Event Subscriptions" and enable events (REQUIRED for Home Tab):
   - Toggle "Enable Events" to On
   - Request URL: `http://localhost:8080/slack/events`
   - Subscribe to bot events: `app_home_opened`
7. Navigate to "App Home" and enable the Home Tab (REQUIRED):
   - Check "Home Tab"
   - Check "Messages Tab"
8. Navigate to "Basic Information":
   - Copy your **Client ID**, **Client Secret**, and **Signing Secret**

### 3. Configure Spotify App

1. Go to [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)
2. Click "Create an App"
3. Name it "StatusBeat" and provide a description
4. Add Redirect URI: `http://localhost:8080/oauth/spotify/callback`
5. Copy your **Client ID** and **Client Secret**

### 4. Set Up MongoDB

**Option A: Local MongoDB**
```bash
# Install MongoDB (macOS with Homebrew)
brew install mongodb-community
brew services start mongodb-community

# Or use Docker
docker run -d -p 27017:27017 --name mongodb mongo:latest
```

**Option B: MongoDB Atlas (Cloud)**
1. Create free account at [MongoDB Atlas](https://www.mongodb.com/cloud/atlas)
2. Create a cluster
3. Get connection string and update in `.env` file

### 5. Configure Environment Variables

Create a `.env` file in the project root (or set environment variables):

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

# MongoDB Configuration (update if using cloud)
SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/statusbeat

# Encryption (generate a strong random key)
ENCRYPTION_SECRET_KEY=your_32_character_secret_key_here
```

**Generate an encryption key:**
```bash
openssl rand -base64 32
```

### 6. Build and Run

```bash
# Build the project
./gradlew build

# Run the application
./gradlew bootRun
```

The application will start on `http://localhost:8080`

## 📱 Usage

### Initial Setup

1. Visit `http://localhost:8080`
2. Click "Get Started - Connect with Slack"
3. Authorize Slack access
4. Authorize Spotify access
5. You're all set! The app will now sync your music status automatically

### Slash Commands

Use these commands in any Slack channel:

- `/statusbeat play` - Resume Spotify playback
- `/statusbeat pause` - Pause Spotify playback
- `/statusbeat status` - Show current sync status and settings
- `/statusbeat sync` - Manually trigger music sync
- `/statusbeat enable` - Enable automatic music sync
- `/statusbeat disable` - Disable automatic music sync
- `/statusbeat help` - Show help message

## ⚙️ Configuration

### Polling Interval

The default polling interval is 10 seconds. To change it, update `application.properties`:

```properties
statusbeat.sync.polling-interval=10000  # milliseconds
```

### Status Template

Users can customize their status template. Default format:
```
{emoji} {title} - {artist}
```

Available placeholders:
- `{emoji}` - User's chosen emoji
- `{title}` - Song title
- `{artist}` - Artist name

### Default Emoji

Change the default music emoji in `application.properties`:

```properties
statusbeat.sync.default-emoji=:headphones:
```

## 🏗️ Architecture

### Project Structure

```
src/main/java/com/statusbeat/statusbeat/
├── config/                 # Configuration classes
│   ├── SecurityConfig.java
│   ├── SlackConfig.java
│   ├── SpotifyConfig.java
│   └── SchedulerConfig.java
├── controller/             # REST controllers
│   ├── OAuthController.java
│   ├── HomeController.java
│   └── SlackController.java
├── model/                  # MongoDB entities
│   ├── User.java
│   └── UserSettings.java
├── repository/             # Data access layer
│   ├── UserRepository.java
│   └── UserSettingsRepository.java
├── service/                # Business logic
│   ├── UserService.java
│   ├── SpotifyService.java
│   ├── SlackService.java
│   └── MusicSyncService.java
├── slack/                  # Slack integrations
│   └── SlackCommandHandler.java
└── util/                   # Utilities
    └── EncryptionUtil.java
```

### Data Flow

1. **Scheduled Sync** (every 10 seconds):
   - `MusicSyncService` retrieves all active users
   - For each user, fetches currently playing track from Spotify
   - Compares with previously playing track
   - If changed, updates Slack status via Slack API

2. **OAuth Flow**:
   - User authorizes Slack → receives access token
   - User authorizes Spotify → receives access + refresh tokens
   - Tokens encrypted and stored in MongoDB

3. **Slash Commands**:
   - Slack sends command to `/slack/events`
   - `SlackCommandHandler` routes to appropriate handler
   - Action performed and response sent back to Slack

## 🔒 Security

- **OAuth Tokens**: Encrypted using AES-256 with PBKDF2 key derivation
- **HTTPS**: Enforced in production (configure reverse proxy)
- **CSRF Protection**: Enabled for web endpoints
- **Rate Limiting**: Built into Slack/Spotify API clients
- **Secrets**: Never commit `.env` file or credentials

## 🧪 Testing

```bash
# Run all tests
./gradlew test

# Run with coverage
./gradlew test jacocoTestReport
```

## 🚢 Deployment

### Cloud Deployment (Example: Heroku)

1. Create Heroku app:
```bash
heroku create statusbeat-app
```

2. Add MongoDB addon:
```bash
heroku addons:create mongolab:sandbox
```

3. Set environment variables:
```bash
heroku config:set SLACK_CLIENT_ID=your_id
heroku config:set SLACK_CLIENT_SECRET=your_secret
# ... set all required env vars
```

4. Update redirect URIs in Slack and Spotify apps to use your Heroku URL

5. Deploy:
```bash
git push heroku main
```

### Docker Deployment

```bash
# Build image
docker build -t statusbeat .

# Run with docker-compose
docker-compose up -d
```

## 🐛 Troubleshooting

### MongoDB Connection Issues
- Ensure MongoDB is running: `brew services list` (macOS)
- Check connection string in `.env`
- Verify network access for MongoDB Atlas

### Slack OAuth Errors
- Verify redirect URI matches exactly in Slack app settings
- Check that all required scopes are added
- Ensure signing secret is correct

### Spotify Token Expired
- Tokens automatically refresh when expired
- If issues persist, re-authorize Spotify connection

### Home Tab Not Loading
- Verify "Event Subscriptions" is enabled in Slack App settings
- Ensure `app_home_opened` event is subscribed under "Subscribe to bot events"
- Check that "Home Tab" is enabled under "App Home" settings
- Reinstall the app to workspace after configuration changes
- Check application logs for errors when opening Home Tab

### Status Not Updating
- Verify sync is enabled: `/statusbeat status`
- Check application logs for errors
- Ensure Spotify is actually playing (not paused)

## 📝 API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/` | GET | Home page |
| `/oauth/slack` | GET | Initiate Slack OAuth |
| `/oauth/slack/callback` | GET | Slack OAuth callback |
| `/oauth/spotify` | GET | Initiate Spotify OAuth |
| `/oauth/spotify/callback` | GET | Spotify OAuth callback |
| `/slack/events` | POST | Slack events endpoint |
| `/success` | GET | Success page |
| `/error` | GET | Error page |
| `/health` | GET | Health check |

## 🤝 Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License.

## 🙏 Acknowledgments

- [Slack Bolt SDK](https://slack.dev/java-slack-sdk/guides/bolt-basics/)
- [Spotify Web API Java](https://github.com/spotify-web-api-java/spotify-web-api-java)
- Spring Boot team

## 📧 Support

For issues and questions:
- Open an issue on GitHub
- Check existing issues and documentation

---

Made with ❤️ for music lovers and Slack users
