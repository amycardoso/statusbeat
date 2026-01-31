# StatusBeat Slash Commands Reference

Quick reference for all available StatusBeat commands in Slack.

## Command Syntax

All commands follow the pattern:
```
/statusbeat <subcommand>
```

## Available Commands

### 🎵 `/statusbeat play`
Resume Spotify playback.

**Example:**
```
/statusbeat play
```

**Response:**
```
▶️ Playback resumed!
```

**Requirements:**
- Spotify account connected
- Spotify client must be active (desktop/mobile/web)

---

### ⏸️ `/statusbeat pause`
Pause Spotify playback.

**Example:**
```
/statusbeat pause
```

**Response:**
```
⏸️ Playback paused!
```

**Requirements:**
- Spotify account connected
- Music currently playing

---

### 📊 `/statusbeat status`
Display your current sync status and settings.

**Example:**
```
/statusbeat status
```

**Response:**
```
🎵 Your StatusBeat Status

Sync Status: ✅ Enabled
Spotify: ✅ Connected
Now Playing: Shape of You - Ed Sheeran

Settings:
• Emoji: 🎵
• Show Artist: Yes
• Show Title: Yes
• Notifications: Disabled
```

---

### 🔄 `/statusbeat sync`
Manually trigger an immediate music sync (bypasses the 10-second polling interval).

**Example:**
```
/statusbeat sync
```

**Response:**
```
🔄 Manual sync triggered!
```

**Use cases:**
- Force immediate status update
- Test if sync is working
- Update status after changing songs

---

### ✅ `/statusbeat enable`
Enable automatic music status synchronization.

**Example:**
```
/statusbeat enable
```

**Response:**
```
✅ Music sync enabled!
```

**What it does:**
- Turns on automatic polling
- Your Slack status will update when songs change
- Syncs every 10 seconds

---

### 🚫 `/statusbeat disable`
Disable automatic music status synchronization.

**Example:**
```
/statusbeat disable
```

**Response:**
```
🚫 Music sync disabled!
```

**What it does:**
- Stops automatic polling
- Your Slack status won't update automatically
- Previous status remains until manually changed
- You can still use `/statusbeat play` and `/statusbeat pause`

---

### ❓ `/statusbeat help`
Display help information with all available commands.

**Example:**
```
/statusbeat help
```

**Response:**
```
🎵 StatusBeat Commands

/statusbeat play - Resume Spotify playback
/statusbeat pause - Pause Spotify playback
/statusbeat status - Show current sync status
/statusbeat sync - Manually trigger music sync
/statusbeat enable - Enable automatic music sync
/statusbeat disable - Disable automatic music sync
/statusbeat help - Show this help message

🔗 To get started, connect your accounts at: /oauth/slack
```

---

## Common Use Cases

### First Time Setup
```
1. Visit http://localhost:8080 (or your deployed URL)
2. Click "Get Started - Connect with Slack"
3. Authorize Slack
4. Authorize Spotify
5. Type /statusbeat status to verify setup
```

### Daily Usage
```
# Enable sync when starting work
/statusbeat enable

# Your status updates automatically as you listen to music

# Disable sync when in meetings
/statusbeat disable
```

### Controlling Playback from Slack
```
# Pause music when someone calls
/statusbeat pause

# Resume after the call
/statusbeat play
```

### Troubleshooting
```
# Check if everything is connected
/statusbeat status

# Force an immediate sync
/statusbeat sync

# If status isn't updating, disable and re-enable
/statusbeat disable
/statusbeat enable
```

## Error Messages

### ❌ "You need to connect your Spotify account first"
**Cause:** Spotify not authorized
**Solution:** Visit the app URL and complete Spotify OAuth

### ❌ "Your Spotify account is not connected"
**Cause:** Spotify token expired or revoked
**Solution:** Re-authorize Spotify through the app

### ❌ "Failed to resume playback"
**Cause:**
- No active Spotify device
- Spotify Premium required
- Network issues

**Solution:**
- Open Spotify on any device
- Ensure Premium subscription
- Check internet connection

### ❌ "User settings not found"
**Cause:** Database issue or first-time setup incomplete
**Solution:** Contact administrator or re-authorize

## Tips & Tricks

### 💡 Quick Status Check
Use `/statusbeat status` to see what's currently playing without switching to Spotify

### 💡 Privacy Mode
Use `/statusbeat disable` when you don't want to share what you're listening to

### 💡 Remote Control
Control your music from Slack without opening Spotify - great for when you're deep in work

### 💡 Team Visibility
Your team can see what you're jamming to - great for discovering new music!

### 💡 Meeting Mode
Create a Slack workflow that automatically runs `/statusbeat disable` when you join a meeting

## Customization

Want to customize your music status? Check the user settings in your database or contact the admin to add a settings UI.

Current customizable options:
- Status emoji (default: 🎵)
- Show/hide artist name
- Show/hide song title
- Status template format
- Enable/disable notifications

## Support

Having issues?
1. Run `/statusbeat status` to diagnose
2. Check the application logs
3. Contact your StatusBeat administrator
4. Open an issue on GitHub

---

Made with ❤️ for music lovers
