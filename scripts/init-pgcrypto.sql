-- Enable pgcrypto extension for AES-256 encryption support
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Create a function to encrypt sensitive data
CREATE OR REPLACE FUNCTION encrypt_sensitive(data TEXT, key TEXT)
RETURNS BYTEA AS $$
BEGIN
  RETURN pgp_sym_encrypt(data, key, 'compress-algo=2, cipher-algo=aes256');
END;
$$ LANGUAGE plpgsql;

-- Create a function to decrypt sensitive data
CREATE OR REPLACE FUNCTION decrypt_sensitive(data BYTEA, key TEXT)
RETURNS TEXT AS $$
BEGIN
  RETURN pgp_sym_decrypt(data, key);
END;
$$ LANGUAGE plpgsql;
