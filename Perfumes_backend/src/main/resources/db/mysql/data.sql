INSERT IGNORE INTO perfumia.roles VALUES (1, "ADMIN"), (2, "USER");

INSERT IGNORE INTO perfumia.users
(user_id, username, email, password, auth_provider, google_subject, description, create_date, roles_rol_id)
VALUES
(1, "admin", "admin@localhost.es", "$2a$10$8vnKWH.lMbjuKq2oGUfrvOdbQ0ab0GqTzpjYRs0s3PssZny3E4DHW", "LOCAL", NULL, "Administrador de PerfumIA", "2026-04-29", 1);
