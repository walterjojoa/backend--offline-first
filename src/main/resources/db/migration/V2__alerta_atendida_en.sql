-- Hora en que alguien marcó la alerta como atendida: permite medir el tiempo de respuesta del cuidador.
ALTER TABLE alertas ADD COLUMN atendida_en TIMESTAMP WITH TIME ZONE;
