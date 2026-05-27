SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0;
SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0;
SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';

CREATE SCHEMA IF NOT EXISTS `perfumia` DEFAULT CHARACTER SET utf8mb4;
USE `perfumia`;

CREATE TABLE IF NOT EXISTS `roles` (
  `rol_id` INT NOT NULL AUTO_INCREMENT,
  `rol_name` VARCHAR(10) NOT NULL,
  PRIMARY KEY (`rol_id`),
  UNIQUE INDEX `rol_name_UNIQUE` (`rol_name` ASC) VISIBLE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS `users` (
  `user_id` INT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(40) NOT NULL,
  `email` VARCHAR(90) NOT NULL,
  `password` CHAR(60) NOT NULL,
  `auth_provider` VARCHAR(20) NULL DEFAULT 'LOCAL',
  `google_subject` VARCHAR(120) NULL,
  `description` LONGTEXT NULL,
  `profile_image_url` VARCHAR(500) NULL,
  `create_date` DATE NOT NULL,
  `roles_rol_id` INT NOT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE INDEX `username_UNIQUE` (`username` ASC) VISIBLE,
  UNIQUE INDEX `email_UNIQUE` (`email` ASC) VISIBLE,
  UNIQUE INDEX `google_subject_UNIQUE` (`google_subject` ASC) VISIBLE,
  INDEX `fk_users_roles_idx` (`roles_rol_id` ASC) VISIBLE,
  CONSTRAINT `fk_users_roles`
    FOREIGN KEY (`roles_rol_id`)
    REFERENCES `roles` (`rol_id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS `perfume_profiles` (
  `profile_id` INT NOT NULL AUTO_INCREMENT,
  `user_id` INT NOT NULL,
  `gender_target` VARCHAR(30) NULL,
  `season` VARCHAR(30) NULL,
  `intensity` VARCHAR(30) NULL,
  `preferred_notes` VARCHAR(255) NULL,
  `disliked_notes` VARCHAR(255) NULL,
  `occasion` VARCHAR(60) NULL,
  `budget` VARCHAR(60) NULL,
  `last_summary` VARCHAR(500) NULL,
  PRIMARY KEY (`profile_id`),
  UNIQUE INDEX `user_id_UNIQUE` (`user_id` ASC) VISIBLE,
  CONSTRAINT `fk_perfume_profiles_users`
    FOREIGN KEY (`user_id`)
    REFERENCES `users` (`user_id`)
    ON DELETE CASCADE
    ON UPDATE NO ACTION
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS `perfume_recommendations` (
  `recommendation_id` INT NOT NULL AUTO_INCREMENT,
  `user_id` INT NOT NULL,
  `perfume_name` VARCHAR(120) NOT NULL,
  `brand` VARCHAR(120) NULL,
  `description` TEXT NULL,
  `season` VARCHAR(80) NULL,
  `notes` VARCHAR(500) NULL,
  `source` VARCHAR(40) NULL,
  `image_url` VARCHAR(500) NULL,
  `accepted` TINYINT(1) NULL DEFAULT 0 COMMENT 'NULL=rechazada, 0=pendiente, 1=aceptada',
  `favorite` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '0=no favorita, 1=favorita',
  `rating` INT NULL COMMENT 'Puntuacion del usuario entre 1 y 5, NULL=sin puntuar',
  `fragella_rating` VARCHAR(20) NULL COMMENT 'Rating externo real devuelto por Fragella, si existe',
  `create_date` DATETIME NOT NULL,
  PRIMARY KEY (`recommendation_id`),
  INDEX `fk_perfume_recommendations_users_idx` (`user_id` ASC) VISIBLE,
  INDEX `idx_perfume_recommendations_user_create_date` (`user_id` ASC, `create_date` ASC) VISIBLE,
  INDEX `idx_perfume_recommendations_user_accepted_create_date` (`user_id` ASC, `accepted` ASC, `create_date` ASC) VISIBLE,
  INDEX `idx_perfume_recommendations_user_perfume_brand_accepted` (`user_id` ASC, `perfume_name` ASC, `brand` ASC, `accepted` ASC) VISIBLE,
  CONSTRAINT `chk_perfume_recommendations_rating`
    CHECK (`rating` IS NULL OR (`rating` BETWEEN 1 AND 5)),
  CONSTRAINT `fk_perfume_recommendations_users`
    FOREIGN KEY (`user_id`)
    REFERENCES `users` (`user_id`)
    ON DELETE CASCADE
    ON UPDATE NO ACTION
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS `chat_messages` (
  `message_id` INT NOT NULL AUTO_INCREMENT,
  `user_id` INT NOT NULL,
  `role_name` VARCHAR(20) NOT NULL,
  `content` TEXT NOT NULL,
  `create_date` DATETIME NOT NULL,
  PRIMARY KEY (`message_id`),
  INDEX `fk_chat_messages_users_idx` (`user_id` ASC) VISIBLE,
  INDEX `idx_chat_messages_user_create_date` (`user_id` ASC, `create_date` ASC) VISIBLE,
  CONSTRAINT `fk_chat_messages_users`
    FOREIGN KEY (`user_id`)
    REFERENCES `users` (`user_id`)
    ON DELETE CASCADE
    ON UPDATE NO ACTION
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS `community_messages` (
  `message_id` INT NOT NULL AUTO_INCREMENT,
  `user_id` INT NOT NULL,
  `content` VARCHAR(280) NOT NULL,
  `create_date` DATETIME NOT NULL,
  PRIMARY KEY (`message_id`),
  INDEX `fk_community_messages_users_idx` (`user_id` ASC) VISIBLE,
  INDEX `idx_community_messages_create_date` (`create_date` ASC) VISIBLE,
  CONSTRAINT `fk_community_messages_users`
    FOREIGN KEY (`user_id`)
    REFERENCES `users` (`user_id`)
    ON DELETE CASCADE
    ON UPDATE NO ACTION
) ENGINE = InnoDB;

DELIMITER $$

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

SET SQL_MODE=@OLD_SQL_MODE;
SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS;
SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS;
