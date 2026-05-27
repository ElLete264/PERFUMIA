-- Migration 001 - FASE 3
-- Objetivo: aplicar mejoras de integridad e indices sobre una base perfumia ya existente sin borrar datos.

USE `perfumia`;

-- 1. Permitir los tres estados de una recomendacion:
--    NULL = rechazada, 0 = pendiente, 1 = aceptada.
ALTER TABLE `perfume_recommendations`
  MODIFY COLUMN `accepted` TINYINT(1) NULL DEFAULT 0
  COMMENT 'NULL=rechazada, 0=pendiente, 1=aceptada';

DELIMITER $$

-- 2. Helper temporal para crear indices solo si no existen.
--    MySQL no soporta CREATE INDEX IF NOT EXISTS de forma portable, por eso se consulta INFORMATION_SCHEMA.
DROP PROCEDURE IF EXISTS `sp_add_index_if_not_exists`$$
CREATE PROCEDURE `sp_add_index_if_not_exists`(
  IN `p_table_name` VARCHAR(64),
  IN `p_index_name` VARCHAR(64),
  IN `p_index_definition` TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM `information_schema`.`statistics`
    WHERE `table_schema` = DATABASE()
      AND `table_name` = `p_table_name`
      AND `index_name` = `p_index_name`
  ) THEN
    SET @sql = CONCAT('CREATE INDEX `', `p_index_name`, '` ON `', `p_table_name`, '` ', `p_index_definition`);
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$

-- 3. Indices compuestos usados por las consultas habituales de chat y recomendaciones.
CALL `sp_add_index_if_not_exists`(
  'chat_messages',
  'idx_chat_messages_user_create_date',
  '(`user_id` ASC, `create_date` ASC)'
)$$

CALL `sp_add_index_if_not_exists`(
  'perfume_recommendations',
  'idx_perfume_recommendations_user_create_date',
  '(`user_id` ASC, `create_date` ASC)'
)$$

CALL `sp_add_index_if_not_exists`(
  'perfume_recommendations',
  'idx_perfume_recommendations_user_accepted_create_date',
  '(`user_id` ASC, `accepted` ASC, `create_date` ASC)'
)$$

CALL `sp_add_index_if_not_exists`(
  'perfume_recommendations',
  'idx_perfume_recommendations_user_perfume_brand_accepted',
  '(`user_id` ASC, `perfume_name` ASC, `brand` ASC, `accepted` ASC)'
)$$

DROP PROCEDURE IF EXISTS `sp_add_index_if_not_exists`$$

-- 4. Trigger de integridad para chat_messages.
--    Valida el rol del mensaje y rellena create_date con NOW() si llega NULL.
DROP TRIGGER IF EXISTS `trg_chat_messages_before_insert`$$
CREATE TRIGGER `trg_chat_messages_before_insert`
BEFORE INSERT ON `chat_messages`
FOR EACH ROW
BEGIN
  IF NEW.`role_name` NOT IN ('USER', 'ASSISTANT', 'SYSTEM') THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Invalid chat_messages.role_name. Allowed values: USER, ASSISTANT, SYSTEM';
  END IF;

  IF NEW.`create_date` IS NULL THEN
    SET NEW.`create_date` = NOW();
  END IF;
END$$

-- 5. Procedimiento de negocio para aceptar recomendaciones de forma segura.
--    Solo permite aceptar una recomendacion si pertenece al usuario indicado.
DROP PROCEDURE IF EXISTS `sp_accept_recommendation`$$
CREATE PROCEDURE `sp_accept_recommendation`(
  IN `p_user_id` BIGINT,
  IN `p_recommendation_id` BIGINT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM `perfume_recommendations`
    WHERE `recommendation_id` = `p_recommendation_id`
      AND `user_id` = `p_user_id`
  ) THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'Recommendation does not exist for this user';
  ELSE
    UPDATE `perfume_recommendations`
    SET `accepted` = 1
    WHERE `recommendation_id` = `p_recommendation_id`
      AND `user_id` = `p_user_id`;
  END IF;
END$$

DELIMITER ;
