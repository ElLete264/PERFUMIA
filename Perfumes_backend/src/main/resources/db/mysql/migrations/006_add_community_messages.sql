-- Migracion 006: chat publico de comunidad.

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
