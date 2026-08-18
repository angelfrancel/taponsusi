ALTER TABLE sellers ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sellers ADD COLUMN verification_token VARCHAR(255);
ALTER TABLE sellers ADD COLUMN verification_token_expires_at TIMESTAMP;

CREATE UNIQUE INDEX idx_sellers_verification_token ON sellers(verification_token)
    WHERE verification_token IS NOT NULL;