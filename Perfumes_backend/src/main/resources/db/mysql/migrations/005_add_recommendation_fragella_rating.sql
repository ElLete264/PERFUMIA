-- Migracion 005: guarda el rating externo real de Fragella por recomendacion.
-- No inventa puntuaciones: si Fragella no devuelve rating, queda NULL.

USE `perfumia`;

SET @fragella_rating_column_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'perfume_recommendations'
    AND COLUMN_NAME = 'fragella_rating'
);

SET @sql := IF(
  @fragella_rating_column_exists = 0,
  'ALTER TABLE `perfume_recommendations` ADD COLUMN `fragella_rating` VARCHAR(20) NULL COMMENT ''Rating externo real devuelto por Fragella, si existe'' AFTER `rating`',
  'SELECT ''fragella_rating already exists'' AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
