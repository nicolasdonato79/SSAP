-- Precio y cantidad "a consultar".
--   precio_usd NULL  = el vendedor no puso precio (se muestra "Precio a consultar").
--   cantidad   NULL  = en el Excel decía "consultar" (se muestra "Disponibilidad a consultar").
ALTER TABLE item_stock ALTER COLUMN precio_usd DROP NOT NULL;
ALTER TABLE item_stock ALTER COLUMN cantidad   DROP NOT NULL;
ALTER TABLE solicitud  ALTER COLUMN precio_usd DROP NOT NULL;
