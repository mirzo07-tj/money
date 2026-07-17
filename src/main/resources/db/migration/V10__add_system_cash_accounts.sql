-- Системный пользователь-касса, представляющий внешний источник денег для пополнений счетов
INSERT INTO roles (name) VALUES ('USER') ON CONFLICT (name) DO NOTHING;

INSERT INTO users (username, email, password_hash, created_at)
VALUES ('SYSTEM_CASH', 'system-cash@bank.internal', 'DISABLED_NO_LOGIN', now())
    ON CONFLICT (username) DO NOTHING;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.username = 'SYSTEM_CASH' AND r.name = 'USER'
    ON CONFLICT DO NOTHING;

INSERT INTO bank_accounts (account_number, user_id, balance, currency, status, created_at, updated_at)
SELECT 'SYS-CASH-TJS', u.id, 0, 'TJS', 'ACTIVE', now(), now()
FROM users u WHERE u.username = 'SYSTEM_CASH'
    ON CONFLICT (account_number) DO NOTHING;

INSERT INTO bank_accounts (account_number, user_id, balance, currency, status, created_at, updated_at)
SELECT 'SYS-CASH-USD', u.id, 0, 'USD', 'ACTIVE', now(), now()
FROM users u WHERE u.username = 'SYSTEM_CASH'
    ON CONFLICT (account_number) DO NOTHING;

INSERT INTO bank_accounts (account_number, user_id, balance, currency, status, created_at, updated_at)
SELECT 'SYS-CASH-EUR', u.id, 0, 'EUR', 'ACTIVE', now(), now()
FROM users u WHERE u.username = 'SYSTEM_CASH'
    ON CONFLICT (account_number) DO NOTHING;

INSERT INTO bank_accounts (account_number, user_id, balance, currency, status, created_at, updated_at)
SELECT 'SYS-CASH-RUB', u.id, 0, 'RUB', 'ACTIVE', now(), now()
FROM users u WHERE u.username = 'SYSTEM_CASH'
    ON CONFLICT (account_number) DO NOTHING;