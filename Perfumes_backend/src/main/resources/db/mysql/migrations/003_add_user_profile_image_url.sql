-- Migracion 003: preparar users para guardar la foto de perfil.
-- No borra datos y solo anade la columna si no existe.

SET @column_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'users'
    AND COLUMN_NAME = 'profile_image_url'
);

SET @sql := IF(
  @column_exists = 0,
  'ALTER TABLE `users` ADD COLUMN `profile_image_url` VARCHAR(500) NULL AFTER `description`',
  'SELECT ''profile_image_url already exists'' AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
