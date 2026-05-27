-- Migracion 004: anadir favoritos y puntuacion a perfume_recommendations.
-- No borra datos. Solo anade columnas/constraint si no existen.

-- favorite: 0=no favorita, 1=favorita.
SET @favorite_column_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'perfume_recommendations'
    AND COLUMN_NAME = 'favorite'
);

SET @sql := IF(
  @favorite_column_exists = 0,
  'ALTER TABLE `perfume_recommendations` ADD COLUMN `favorite` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''0=no favorita, 1=favorita'' AFTER `accepted`',
  'SELECT ''favorite already exists'' AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- rating: puntuacion opcional del usuario entre 1 y 5.
SET @rating_column_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'perfume_recommendations'
    AND COLUMN_NAME = 'rating'
);

SET @sql := IF(
  @rating_column_exists = 0,
  'ALTER TABLE `perfume_recommendations` ADD COLUMN `rating` INT NULL COMMENT ''Puntuacion del usuario entre 1 y 5, NULL=sin puntuar'' AFTER `favorite`',
  'SELECT ''rating already exists'' AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Constraint para mantener rating entre 1 y 5 o NULL.
SET @rating_constraint_exists := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'perfume_recommendations'
    AND CONSTRAINT_NAME = 'chk_perfume_recommendations_rating'
);

SET @sql := IF(
  @rating_constraint_exists = 0,
  'ALTER TABLE `perfume_recommendations` ADD CONSTRAINT `chk_perfume_recommendations_rating` CHECK (`rating` IS NULL OR (`rating` BETWEEN 1 AND 5))',
  'SELECT ''chk_perfume_recommendations_rating already exists'' AS message'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
