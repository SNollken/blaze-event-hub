ALTER TABLE events ADD COLUMN IF NOT EXISTS creator_channel_slug VARCHAR(255);
ALTER TABLE events ADD COLUMN IF NOT EXISTS creator_channel_display_name VARCHAR(255);
ALTER TABLE events ADD COLUMN IF NOT EXISTS creator_channel_avatar_url VARCHAR(512);

CREATE INDEX IF NOT EXISTS idx_events_channel_slug ON events(creator_channel_slug);
