ALTER TABLE users
ADD COLUMN IF NOT EXISTS default_link_expiration VARCHAR(20);

UPDATE users
SET default_link_expiration = 'never'
WHERE default_link_expiration IS NULL;

ALTER TABLE users
ALTER COLUMN default_link_expiration SET DEFAULT 'never';

ALTER TABLE users
ALTER COLUMN default_link_expiration SET NOT NULL;
