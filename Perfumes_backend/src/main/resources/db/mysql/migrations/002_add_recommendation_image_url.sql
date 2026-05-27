-- Migracion 002: guardar la imagen de botella devuelta por Fragella.
-- No borra datos y solo anade la columna si no existe.

SET @column_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'perfume_recommendations'
    AND COLUMN_NAME = 'image_url'
);

SET @sql := IF(
  @column_exists = 0,
  'ALTER TABLE `perfume_recommendations` ADD COLUMN `image_url` VARCHAR(500) NULL AFTER `source`',
  'SELECT ''image_url already exists'' AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
