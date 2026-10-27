# El servicio de IA. Imagen aparte del backend porque son dos lenguajes distintos; se despliegan
# juntos desde este mismo repositorio (ver render.yaml en la raiz).
FROM python:3.11-slim

WORKDIR /app

# Las dependencias primero y en su propia capa: mientras requirements.txt no cambie, reconstruir
# la imagen no vuelve a bajar numpy ni scikit-learn, que son lo que tarda.
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY ia ./ia

# No correr como root dentro del contenedor, igual que el backend
RUN useradd --system --uid 1002 lacocha
USER lacocha

# Render manda el puerto en la variable PORT; 8000 es para correrlo en la maquina de uno.
ENV PORT=8000
EXPOSE 8000

# Un solo worker: el plan gratuito tiene 512 MB y cada worker carga su propia copia de numpy y
# scikit-learn. Dos workers en 512 MB se quedan sin memoria antes de servir nada.
CMD ["sh", "-c", "uvicorn ia.api:app --host 0.0.0.0 --port ${PORT} --workers 1"]
