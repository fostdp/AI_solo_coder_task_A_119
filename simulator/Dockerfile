FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY balance_simulator.py .
ENV MQTT_BROKER=mosquitto
ENV MQTT_PORT=1883
ENV MQTT_USERNAME=admin
ENV MQTT_PASSWORD=public
ENV SIM_MODE=fast
CMD ["python", "balance_simulator.py", "--mode", "${SIM_MODE}"]
