# StatusBeat Setup Guide

Step-by-step instructions for setting up StatusBeat.

## Prerequisites

- Java 25 installed (`java -version`)
- Gradle installed (or use wrapper `./gradlew`)
- MongoDB installed or access to MongoDB Atlas
- Slack workspace with admin permissions
- Spotify account (Premium required for playback control)

## Step 1: Install Java 25

**macOS (using SDKMAN):**
```bash
curl -s "https://get.sdkman.io" | bash
sdk install java 25-tem
```

**Or using Homebrew:**
```bash
brew install openjdk@25
```

**Verify:**
```bash
java -version
```

## Step 2: Install MongoDB

**Option A: Local (macOS)**
```bash
brew tap mongodb/brew
brew install mongodb-community@7.0
brew services start mongodb-community@7.0
```

**Option B: Docker**
```bash
docker run -d -p 27017:27017 --name statusbeat-mongodb mongo:7.0
```

**Option C: MongoDB Atlas**
1. Create account at [MongoDB Atlas](https://www.mongodb.com/cloud/atlas)
2. Create a cluster
3. Get connection string for `.env`

## Step 3: Create Slack App

1. Go to [Slack API Console](https://api.slack.com/apps)
2. Click "Create New App" → "From scratch"
3. Name: `StatusBeat`, select your workspace

**OAuth & Permissions:**
- Redirect URL: `http://localhost:8080/oauth/slack/callback`
- User Token Scopes (required):
  - `users.profile:write`
  - `users.profile:read`
- Bot Token Scopes (optional):
  - `chat:write`

**Slash Commands:**
- Command: `/statusbeat`
- Request URL: `http://localhost:8080/slack/events`
- Description: `Control StatusBeat music sync`

**Event Subscriptions (required for Home Tab):**
- Enable Events: On
- Request URL: `http://localhost:8080/slack/events`
- Subscribe to bot events: `app_home_opened`

**App Home:**
- Enable "Home Tab"
- Enable "Messages Tab"

**Basic Information:**
- Copy Client ID, Client Secret, and Signing Secret

## Step 4: Create Spotify App

1. Go to [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)
2. Click "Create an App"
3. Name: `StatusBeat`
4. Redirect URI: `http://localhost:8080/oauth/spotify/callback`
5. Copy Client ID and Client Secret

## Step 5: Configure Environment

```bash
cp .env.example .env
```

Generate encryption key:
```bash
openssl rand -base64 32
```

Edit `.env`:
```bash
# Slack Configuration
SLACK_CLIENT_ID=your_client_id
SLACK_CLIENT_SECRET=your_client_secret
SLACK_SIGNING_SECRET=your_signing_secret
SLACK_REDIRECT_URI=http://localhost:8080/oauth/slack/callback

# Spotify Configuration
SPOTIFY_CLIENT_ID=your_client_id
SPOTIFY_CLIENT_SECRET=your_client_secret
SPOTIFY_REDIRECT_URI=http://localhost:8080/oauth/spotify/callback

# MongoDB
SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/statusbeat

# Encryption
ENCRYPTION_SECRET_KEY=your_generated_key
```

## Step 6: Build and Run

Load environment variables:
```bash
export $(cat .env | xargs)
```

Build and run:
```bash
./gradlew build
./gradlew bootRun
```

Verify at http://localhost:8080

## Step 7: Test Setup

1. Click "Connect to Slack" and authorize
2. Authorize Spotify
3. In Slack, type `/statusbeat help`
4. Play a song on Spotify
5. Check your Slack status after 10-15 seconds

## Docker (Optional)

```bash
docker-compose up -d
docker-compose logs -f app
```

## Troubleshooting

**Application won't start:**
```bash
java -version  # Should be 25+
brew services list | grep mongodb  # Check MongoDB
echo $SLACK_CLIENT_ID  # Verify env vars
```

**Slack OAuth fails:**
- Verify redirect URI matches exactly
- Ensure all required scopes are added

**Spotify OAuth fails:**
- Verify redirect URI in Spotify settings
- Spotify Premium required for playback control

**Status not updating:**
```bash
/statusbeat status  # Check sync is enabled
/statusbeat sync    # Force manual sync
```

**MongoDB issues:**
```bash
brew services start mongodb-community@7.0
```

For MongoDB Atlas: whitelist your IP in Network Access.

## Production Checklist

- Update redirect URIs in Slack and Spotify apps
- Use HTTPS
- Use production MongoDB instance
- Generate strong encryption key
- Configure logging and monitoring
- Set up backups for MongoDB
