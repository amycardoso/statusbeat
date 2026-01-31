# StatusBeat Commands Reference

All commands follow the pattern: `/statusbeat <subcommand>`

## Available Commands

### `/statusbeat play`

Resume Spotify playback.

```
/statusbeat play
```

Requirements:
- Spotify account connected
- Spotify client active (desktop/mobile/web)

### `/statusbeat pause`

Pause Spotify playback.

```
/statusbeat pause
```

Requirements:
- Spotify account connected
- Music currently playing

### `/statusbeat status`

Display current sync status and settings.

```
/statusbeat status
```

Shows: sync status, Spotify connection, currently playing track, and settings.

### `/statusbeat sync`

Manually trigger an immediate music sync.

```
/statusbeat sync
```

Use when you want to force an immediate status update.

### `/statusbeat enable`

Enable automatic music status synchronization.

```
/statusbeat enable
```

Turns on automatic polling every 10 seconds.

### `/statusbeat disable`

Disable automatic music status synchronization.

```
/statusbeat disable
```

Stops automatic polling. Playback commands still work.

### `/statusbeat reconnect`

Reconnect your Spotify account.

```
/statusbeat reconnect
```

Use when your Spotify connection has expired or needs to be refreshed.

### `/statusbeat help`

Display help with all available commands.

```
/statusbeat help
```

## Common Workflows

**Daily usage:**
```
/statusbeat enable     # Start of day
/statusbeat disable    # During meetings
/statusbeat enable     # After meetings
```

**Playback control:**
```
/statusbeat pause      # Pause for a call
/statusbeat play       # Resume after
```

**Troubleshooting:**
```
/statusbeat status     # Check connection
/statusbeat sync       # Force update
/statusbeat reconnect  # Fix Spotify connection
```

## Error Messages

| Error | Cause | Solution |
|-------|-------|----------|
| "Connect your Spotify account first" | Spotify not authorized | Complete OAuth at app URL |
| "Spotify account not connected" | Token expired | Re-authorize or use `/statusbeat reconnect` |
| "Failed to resume playback" | No active device or no Premium | Open Spotify, verify Premium subscription |
| "User settings not found" | Setup incomplete | Re-authorize through app |

## Tips

- Use `/statusbeat status` to check what's playing without switching apps
- Use `/statusbeat disable` for privacy during sensitive listening
- Control playback from Slack without opening Spotify

## Customization

Configurable options:
- Status emoji (default: `:musical_note:`)
- Status template format (default: `{title} - {artist}`)
- Show/hide artist name
- Show/hide song title

## Support

1. Run `/statusbeat status` to diagnose
2. Check application logs
3. Open an issue on GitHub
