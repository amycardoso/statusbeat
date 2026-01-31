# 🚀 StatusBeat Setup Guide

This guide will walk you through setting up StatusBeat step by step.

## Prerequisites Checklist

- [ ] Java 25 installed (`java -version`)
- [ ] Gradle installed (or use wrapper `./gradlew`)
- [ ] MongoDB installed or access to MongoDB Atlas
- [ ] Slack workspace with admin permissions
- [ ] Spotify account (Premium recommended for playback control)

## Step-by-Step Setup

### 1. Install Java 25

**macOS (using SDKMAN):**
```bash
curl -s "https://get.sdkman.io" | bash
sdk install java 25-tem
```

**Or using Homebrew:**
```bash
brew install openjdk@21
```

**Verify installation:**
```bash
java -version
```

### 2. Install and Start MongoDB

**Option A: Local MongoDB (macOS)**
```bash
# Install MongoDB
brew tap mongodb/brew
brew install mongodb-community@7.0

# Start MongoDB service
brew services start mongodb-community@7.0

# Verify MongoDB is running
brew services list | grep mongodb
```

**Option B: Docker**
```bash
docker run -d -p 27017:27017 --name statusbeat-mongodb mongo:7.0
```

**Option C: MongoDB Atlas (Cloud)**
1. Go to [MongoDB Atlas](https://www.mongodb.com/cloud/atlas)
2. Create a free account
3. Create a new cluster (Free tier available)
4. Click "Connect" → "Connect your application"
5. Copy the connection string
6. Save it for later (you'll need it in `.env`)

### 3. Create Slack App

1. **Go to Slack API Console**
   - Visit: https://api.slack.com/apps
   - Click "Create New App" → "From scratch"
   - App Name: `StatusBeat`
   - Workspace: Select your workspace
   - Click "Create App"

2. **Configure OAuth & Permissions**
   - In left sidebar, click "OAuth & Permissions"
   - Scroll to "Redirect URLs"
   - Add: `http://localhost:8080/oauth/slack/callback`
   - For production, also add your deployed URL

3. **Add OAuth Scopes**
   - Scroll to "Scopes" section
   - **IMPORTANT**: Under "User Token Scopes" (NOT Bot Token Scopes), add:
     - `users.profile:write` - Required for updating user's status
     - `users.profile:read` - Required for reading user's profile
   - Under "Bot Token Scopes" (optional), add:
     - `chat:write` - Optional, for sending messages

4. **Create Slash Command**
   - In left sidebar, click "Slash Commands"
   - Click "Create New Command"
   - Command: `/statusbeat`
   - Request URL: `http://localhost:8080/slack/events`
   - Short Description: `Control your music sync`
   - Usage Hint: `[play|pause|status|help]`
   - Click "Save"

5. **Enable Event Subscriptions (REQUIRED for Home Tab)**
   - In left sidebar, click "Event Subscriptions"
   - Toggle "Enable Events" to On
   - Request URL: `http://localhost:8080/slack/events`
   - Wait for verification ✓
   - Scroll down to "Subscribe to bot events"
   - Click "Add Bot User Event"
   - Add: `app_home_opened` (Required for Home Tab to work)
   - Click "Save Changes"

6. **Enable App Home Tab**
   - In left sidebar, click "App Home"
   - Under "Show Tabs" section:
     - Check ✓ "Home Tab" (REQUIRED for Home UI)
     - Check ✓ "Messages Tab" (for direct messages)
   - Click "Save Changes"

7. **Get Your Credentials**
   - Go to "Basic Information"
   - Under "App Credentials":
     - Copy **Client ID**
     - Copy **Client Secret**
     - Copy **Signing Secret**
   - Save these for the next step

### 4. Reinstall the Slack App (if you already installed it before)

If you previously installed the app without these event subscriptions:
1. Go to "Install App" in left sidebar
2. Click "Reinstall to Workspace"
3. Authorize the updated permissions

### 5. Create Spotify App

1. **Go to Spotify Developer Dashboard**
   - Visit: https://developer.spotify.com/dashboard
   - Log in with your Spotify account
   - Click "Create an App"

2. **Configure App**
   - App Name: `StatusBeat`
   - App Description: `Music-Slack Status Sync`
   - Redirect URIs: Add `http://localhost:8080/oauth/spotify/callback`
   - Check the agreement box
   - Click "Create"

3. **Get Credentials**
   - Click "Settings"
   - Copy **Client ID**
   - Click "View Client Secret" and copy it
   - Save these for the next step

### 6. Configure Environment Variables

1. **Copy the example file:**
   ```bash
   cp .env.example .env
   ```

2. **Generate an encryption key:**
   ```bash
   openssl rand -base64 32
   ```

3. **Edit `.env` file:**
   ```bash
   # Use your favorite editor
   nano .env
   # or
   vim .env
   # or
   code .env
   ```

4. **Fill in all the values:**
   ```bash
   # Slack Configuration (from Step 3)
   SLACK_CLIENT_ID=123456789.987654321
   SLACK_CLIENT_SECRET=abcdef1234567890abcdef1234567890
   SLACK_SIGNING_SECRET=1234567890abcdef1234567890abcdef
   SLACK_REDIRECT_URI=http://localhost:8080/oauth/slack/callback

   # Spotify Configuration (from Step 4)
   SPOTIFY_CLIENT_ID=abcdef1234567890abcdef1234567890
   SPOTIFY_CLIENT_SECRET=abcdef1234567890abcdef1234567890
   SPOTIFY_REDIRECT_URI=http://localhost:8080/oauth/spotify/callback

   # MongoDB Configuration
   # For local MongoDB:
   SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/statusbeat

   # For MongoDB Atlas:
   # SPRING_DATA_MONGODB_URI=mongodb+srv://username:password@cluster.mongodb.net/statusbeat

   # Encryption (from generated key)
   ENCRYPTION_SECRET_KEY=your_generated_key_from_step_2
   ```

### 7. Build and Run the Application

1. **Load environment variables:**
   ```bash
   # Option A: Export manually (macOS/Linux)
   export $(cat .env | xargs)

   # Option B: Use direnv (recommended)
   brew install direnv
   echo 'eval "$(direnv hook bash)"' >> ~/.bashrc  # or ~/.zshrc
   direnv allow
   ```

2. **Build the project:**
   ```bash
   ./gradlew build
   ```

3. **Run the application:**
   ```bash
   ./gradlew bootRun
   ```

   Or run the JAR directly:
   ```bash
   java -jar build/libs/statusbeat-0.0.1-SNAPSHOT.jar
   ```

4. **Verify it's running:**
   - Open browser to http://localhost:8080
   - You should see the StatusBeat home page

### 8. Test the Setup

1. **Connect your accounts:**
   - Click "Get Started - Connect with Slack"
   - Authorize Slack access
   - Authorize Spotify access
   - You should see a success page

2. **Test Slack commands:**
   - Open Slack
   - Type `/statusbeat help`
   - You should see the help message

3. **Test the Home Tab:**
   - Open Slack
   - Go to "Apps" in the sidebar
   - Click on "StatusBeat"
   - Click the "Home" tab
   - You should see your Home UI with connection status and settings!

4. **Test music sync:**
   - Play a song on Spotify
   - Wait 10-15 seconds
   - Check your Slack status - it should update with the song info

### 9. Using Docker (Optional)

If you prefer Docker:

1. **Build and run with docker-compose:**
   ```bash
   docker-compose up -d
   ```

2. **View logs:**
   ```bash
   docker-compose logs -f app
   ```

3. **Stop services:**
   ```bash
   docker-compose down
   ```

## Troubleshooting

### Application won't start

**Check Java version:**
```bash
java -version
# Should be 21 or higher
```

**Check MongoDB:**
```bash
# For local MongoDB
brew services list | grep mongodb

# For Docker
docker ps | grep mongo
```

**Check environment variables:**
```bash
echo $SLACK_CLIENT_ID
# Should print your client ID
```

### Slack OAuth fails

- Verify redirect URI in Slack app settings matches exactly
- Check that you're using the correct workspace
- Ensure all required scopes are added

### Spotify OAuth fails

- Verify redirect URI in Spotify app settings
- Check that your Spotify account is active
- For playback control, Spotify Premium is required

### Status not updating

1. **Check sync is enabled:**
   ```bash
   # In Slack
   /statusbeat status
   ```

2. **Check logs:**
   ```bash
   # View application logs
   tail -f logs/statusbeat.log

   # Or if using docker-compose
   docker-compose logs -f app
   ```

3. **Manual sync:**
   ```bash
   # In Slack
   /statusbeat sync
   ```

### MongoDB connection issues

**For local MongoDB:**
```bash
# Check if MongoDB is running
brew services list

# Start MongoDB if not running
brew services start mongodb-community@7.0

# Check logs
tail -f /usr/local/var/log/mongodb/mongo.log
```

**For MongoDB Atlas:**
- Whitelist your IP address in Atlas Network Access
- Check username/password in connection string
- Ensure database user has read/write permissions

## Production Deployment Checklist

When deploying to production:

- [ ] Update redirect URIs in Slack and Spotify apps
- [ ] Use HTTPS (required for production)
- [ ] Set up proper MongoDB instance (not local)
- [ ] Generate strong encryption key (32+ characters)
- [ ] Set up environment variables in hosting platform
- [ ] Configure logging to file/service
- [ ] Set up monitoring and alerts
- [ ] Enable CORS if needed for frontend
- [ ] Configure rate limiting
- [ ] Set up backups for MongoDB
- [ ] Use secrets manager for credentials
- [ ] Configure firewall rules

## Next Steps

- Read the [README.md](README.md) for complete documentation
- Customize your status template
- Invite team members to use StatusBeat
- Report issues or contribute on GitHub

## Getting Help

If you encounter issues:

1. Check the troubleshooting section above
2. Review application logs
3. Check [GitHub Issues](https://github.com/your-repo/statusbeat/issues)
4. Create a new issue with:
   - Steps to reproduce
   - Error messages
   - Environment details (OS, Java version, etc.)

---

Happy syncing! 🎵
