# PerfumIA Backend

Backend de PerfumIA, aplicacion TFG para recomendar perfumes mediante una conversacion con IA.

La documentacion completa del proyecto esta en el README principal de la carpeta `APP_PERFUMES`.

## Base de Datos

El esquema MySQL esta en:

```txt
src/main/resources/db/mysql/schema.sql
```

La migracion para bases ya existentes esta en:

```txt
src/main/resources/db/mysql/migrations/001_update_recommendations_and_chat_integrity.sql
```

En `perfume_recommendations.accepted` se usan tres estados:

- `NULL`: recomendacion rechazada.
- `0`: recomendacion pendiente.
- `1`: recomendacion aceptada.

La base incluye el trigger `trg_chat_messages_before_insert` para validar `chat_messages.role_name` (`USER`, `ASSISTANT` o `SYSTEM`) y rellenar `create_date` con `NOW()` si llega vacio.

Tambien incluye el procedimiento `sp_accept_recommendation(p_user_id, p_recommendation_id)`, que acepta una recomendacion solo si pertenece al usuario indicado.

Para aplicar la migracion en MySQL Workbench, abre el archivo de migracion y ejecutalo completo, porque usa `DELIMITER $$`.
