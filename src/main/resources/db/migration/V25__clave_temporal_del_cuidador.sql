-- No se pide correo al registrarse: varios cuidadores no tienen uno. Eso deja sin salida al que
-- olvida la clave, porque no hay a donde mandarle un enlace.
--
-- La salida es la que ya existe en la finca: el productor se la reinicia. El sistema le genera una
-- clave temporal, el productor se la dice al cuidador, y el cuidador la cambia la primera vez que
-- entra. Esta columna es la que obliga ese cambio: mientras este en true, la clave que hay sirve
-- para entrar y para nada mas.
ALTER TABLE usuarios ADD COLUMN debe_cambiar_clave BOOLEAN NOT NULL DEFAULT FALSE;

-- Quien reinicio la clave y cuando. Es la bitacora de una operacion delicada: con ella el productor
-- puede entrar como si fuera otra persona, y eso tiene que quedar escrito.
ALTER TABLE usuarios ADD COLUMN clave_reiniciada_en TIMESTAMP WITH TIME ZONE;
ALTER TABLE usuarios ADD COLUMN clave_reiniciada_por UUID REFERENCES usuarios (id);
